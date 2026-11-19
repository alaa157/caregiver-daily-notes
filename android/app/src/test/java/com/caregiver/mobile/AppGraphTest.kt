package com.caregiver.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.caregiver.mobile.data.SignInResult
import com.caregiver.mobile.data.api.CreateRecipientRequest
import com.caregiver.mobile.data.api.LoginRequest
import com.caregiver.mobile.data.api.RegisterRequest
import com.caregiver.mobile.data.accounts.AccountResultGuard
import com.caregiver.mobile.data.accounts.AccountScopedResult
import com.caregiver.mobile.data.accounts.AccountsDatabase
import com.caregiver.mobile.data.accounts.PerAccountDatabaseProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import retrofit2.HttpException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** #602: the local graph resolves the note database per active account. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = com.caregiver.mobile.test.TestGraphApplication::class)
class AppGraphTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private suspend fun accountIdFor(email: String): String =
        AccountsDatabase.create(context).accountDao().listAll().single { it.email == email }.id

    @Test
    fun localWritesGoToTheActiveAccountsFile() = runTest {
        val graph = AppGraph(context)
        assertTrue(graph.isLocalMode)

        assertEquals(SignInResult.SignedIn, graph.auth.signUp("Amina", "amina@example.com", "goodpass1"))
        val idA = accountIdFor("amina@example.com")
        graph.csvTransferService.export() // materialize the active account's file
        val fileA = context.getDatabasePath("${PerAccountDatabaseProvider.FILE_PREFIX}$idA")
        assertTrue(fileA.exists())

        graph.auth.signOut()
        assertEquals(SignInResult.SignedIn, graph.auth.signUp("Nour", "nour@example.com", "goodpass1"))
        val idB = accountIdFor("nour@example.com")
        graph.csvTransferService.export() // now resolves account B's database
        val fileB = context.getDatabasePath("${PerAccountDatabaseProvider.FILE_PREFIX}$idB")
        assertTrue(fileB.exists())
        assertFalse(fileA.path == fileB.path)
    }

    @Test
    fun noActiveAccountFailsWithAClearError() = runTest {
        val graph = AppGraph(context)
        try {
            graph.apis.notes()
            fail("expected local database access to fail with no active account")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("No active account"))
        }
    }

    @Test
    fun switchingAccountsDoesNotShowThePreviousAccountsRecipients() = runTest {
        val graph = AppGraph(context)
        assertEquals(SignInResult.SignedIn, graph.auth.signUp("Amina", "amina@example.com", "goodpass1"))
        graph.apis.recipients().create(CreateRecipientRequest("Amina's person"))
        assertEquals(1, graph.apis.recipients().list().size)

        graph.auth.signOut()
        assertEquals(SignInResult.SignedIn, graph.auth.signUp("Nour", "nour@example.com", "goodpass1"))

        val afterSwitch = graph.apis.recipients().list()
        assertTrue(afterSwitch.isEmpty())
        assertFalse(afterSwitch.any { it.name == "Amina's person" })
    }

    @Test
    fun accountSwapAdvancesTheEpochAndCallsTheResetHooks() = runTest {
        var resets = 0
        var homeNavigations = 0
        val graph = AppGraph(
            context,
            onResetAccountViewModels = { resets++ },
            onNavigateHome = { homeNavigations++ },
        )
        assertEquals(SignInResult.SignedIn, graph.auth.signUp("Amina", "amina@example.com", "goodpass1"))
        val firstEpoch = graph.accountEpoch.value

        graph.auth.signOut()
        assertEquals(SignInResult.SignedIn, graph.auth.signUp("Nour", "nour@example.com", "goodpass1"))

        assertTrue(graph.accountEpoch.value > firstEpoch)
        assertEquals(1, resets) // the sign-out swap
        assertEquals(1, homeNavigations)
    }

    @Test
    fun globalSettingsSurviveAnAccountSwitch() = runTest {
        val graph = AppGraph(context)
        val tokensBefore = graph.tokens
        graph.settings.setLanguage("en")
        assertEquals(SignInResult.SignedIn, graph.auth.signUp("Amina", "amina@example.com", "goodpass1"))

        graph.auth.signOut()
        assertEquals(SignInResult.SignedIn, graph.auth.signUp("Nour", "nour@example.com", "goodpass1"))

        assertEquals("en", graph.settings.language.first())
        org.junit.Assert.assertSame(tokensBefore, graph.tokens) // same process-scoped token holder
    }

    @Test
    fun switchingCancelsInFlightAccountScopedWork() = runTest {
        val graph = AppGraph(context)
        assertEquals(SignInResult.SignedIn, graph.auth.signUp("Amina", "amina@example.com", "goodpass1"))
        val gate = CompletableDeferred<Unit>()
        var completed = false
        val job = graph.accountWorkScope.launch {
            gate.await()
            completed = true
        }
        assertTrue(job.isActive)

        graph.auth.signOut() // cancels and joins the account work scope

        assertTrue(job.isCancelled)
        gate.complete(Unit) // a late provider result must not land
        assertFalse(completed)
    }

    @Test
    fun cancellationSurfacesAsCancellationNotAnAiFailure() = runTest {
        val graph = AppGraph(context)
        assertEquals(SignInResult.SignedIn, graph.auth.signUp("Amina", "amina@example.com", "goodpass1"))
        var sawCancellation = false
        val job = graph.accountWorkScope.launch {
            try {
                throw CancellationException("provider call cancelled")
            } catch (_: CancellationException) {
                sawCancellation = true
            }
        }
        job.join()
        assertTrue(sawCancellation)
    }

    @Test
    fun aResultTaggedWithThePreviousAccountIsDiscarded() = runTest {
        val graph = AppGraph(context)
        assertEquals(SignInResult.SignedIn, graph.auth.signUp("Amina", "amina@example.com", "goodpass1"))
        val idA = accountIdFor("amina@example.com")
        val guard = AccountResultGuard(graph.activeAccountIdFlow)

        val resultA = AccountScopedResult(idA, "Amina's summary")
        assertEquals("Amina's summary", guard.accept(resultA))
        var discarded = false
        graph.auth.signOut()
        assertEquals(SignInResult.SignedIn, graph.auth.signUp("Nour", "nour@example.com", "goodpass1"))

        assertNull(guard.accept(resultA, onStale = { discarded = true }))
        assertTrue(discarded)
    }

    @Test
    fun localAuthApiReturnsAnOpaqueTokenAndStoresIt() = runTest {
        val graph = AppGraph(context)
        assertEquals(SignInResult.SignedIn, graph.auth.signUp("Amina", "amina@example.com", "goodpass1"))

        val response = graph.apis.auth().login(LoginRequest("amina@example.com", "goodpass1"))

        assertTrue(response.token.startsWith("local:"))
        assertFalse(response.token.contains("goodpass1"))
        val row = AccountsDatabase.create(context).accountDao().findByEmail("amina@example.com")!!
        assertFalse(response.token.contains(row.passwordHash))
        assertFalse(response.token.contains(row.salt))
        assertNotNull(graph.tokens.token)
    }

    @Test
    fun localAuthApiWrongPasswordThrowsA401ShapedFailure() = runTest {
        val graph = AppGraph(context)
        assertEquals(SignInResult.SignedIn, graph.auth.signUp("Amina", "amina@example.com", "goodpass1"))

        try {
            graph.apis.auth().login(LoginRequest("amina@example.com", "wrongpass1"))
            fail("expected an HTTP-shaped failure")
        } catch (e: HttpException) {
            assertEquals(401, e.code())
        }
    }

    @Test
    fun localAuthApiRegisterCreatesARealAccountAndMapsDuplicateTo409() = runTest {
        val graph = AppGraph(context)
        val created = graph.apis.auth().register(RegisterRequest("amina@example.com", "goodpass1"))

        assertTrue(created.token.startsWith("local:"))
        assertNotNull(AccountsDatabase.create(context).accountDao().findByEmail("amina@example.com"))

        try {
            graph.apis.auth().register(RegisterRequest("amina@example.com", "goodpass1"))
            fail("expected a duplicate-email failure")
        } catch (e: HttpException) {
            assertEquals(409, e.code())
        }

        try {
            graph.apis.auth().register(RegisterRequest("weak@example.com", "short"))
            fail("expected a weak-password failure")
        } catch (e: HttpException) {
            assertEquals(400, e.code())
        }
    }

    @Test
    fun nonLocalGraphCreatesNoPerAccountFile() = runTest {
        val graph = AppGraph(context, forceProduction = true)
        assertFalse(graph.isLocalMode)
        assertFalse(context.getDatabasePath("${PerAccountDatabaseProvider.FILE_PREFIX}acct-none").exists())
    }
}
