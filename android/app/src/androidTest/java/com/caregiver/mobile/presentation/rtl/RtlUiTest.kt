package com.caregiver.mobile.presentation.rtl

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.network.TokenHolder
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.FakeAuthApi
import com.caregiver.mobile.data.FakeBackendApis
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.presentation.auth.AuthForm
import com.caregiver.mobile.presentation.auth.AuthMode
import com.caregiver.mobile.presentation.auth.AuthViewModel
import com.caregiver.mobile.presentation.home.HomeScreen
import com.caregiver.mobile.presentation.notes.NoteEditorScreen
import com.caregiver.mobile.presentation.plans.PlansScreen
import com.caregiver.mobile.presentation.recipients.RecipientsScreen
import com.caregiver.mobile.presentation.summary.SummaryPeriodScreen
import java.util.Locale
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.caregiver.mobile.test.WithTestOwner

/**
 * Task 8 RTL coverage: every required screen renders its labels in Arabic
 * and English, and the composition actually uses [LayoutDirection.Rtl] for
 * Arabic and [LayoutDirection.Ltr] for English (driven by the locale, never
 * hard-coded). Icon-only controls expose localized descriptions.
 * Instrumentation-only; needs a device or emulator to execute.
 */
@RunWith(AndroidJUnit4::class)
class RtlUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var server: MockWebServer
    private lateinit var graph: AppGraph
    private var capturedDirection: LayoutDirection? = null

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        graph = AppGraph(context)
        runBlocking {
            graph.settings.setBaseUrl(server.url("/").toString())
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueList() {
        server.enqueue(MockResponse().setBody("""[{"id":"r1","name":"Omar عمر","active":true}]"""))
        server.enqueue(MockResponse().setBody("""[{"id":"r1","name":"Omar عمر","active":true}]"""))
    }

    private fun enqueueHome(notesBody: String) {
        server.enqueue(MockResponse().setBody("""[{"id":"r1","name":"Omar عمر","active":true}]"""))
        server.enqueue(MockResponse().setBody(notesBody))
    }

    private fun enqueuePlans(plansBody: String) {
        server.enqueue(MockResponse().setBody("""[{"id":"r1","name":"Omar عمر","active":true}]"""))
        server.enqueue(MockResponse().setBody(plansBody))
    }

    @Test
    fun loginArabicRtl() {
        var switched = false
        renderAuth(AuthMode.Login, "ar") { switched = true }

        assertEquals(LayoutDirection.Rtl, capturedDirection)
        compose.onNodeWithText("تسجيل الدخول").assertIsDisplayed()
        compose.onNodeWithText("البريد الإلكتروني").assertIsDisplayed()
        compose.onNodeWithText("دخول").assertIsDisplayed()
    }

    @Test
    fun loginEnglishLtr() {
        renderAuth(AuthMode.Login, "en") {}

        assertEquals(LayoutDirection.Ltr, capturedDirection)
        compose.onNodeWithText("Sign in").assertIsDisplayed()
        compose.onNodeWithText("Email").assertIsDisplayed()
    }

    @Test
    fun homeArabicRtlWithMixedName() {
        enqueueHome("[]")
        render("ar") { HomeScreen(graph, rememberNavController()) }

        assertEquals(LayoutDirection.Rtl, capturedDirection)
        compose.onNodeWithText(Bidi.isolate("Omar عمر")).assertIsDisplayed()
        compose.onNodeWithText("0 / 1 مكتملة").assertIsDisplayed()
        compose.onNodeWithText("لا ملاحظات بعد").assertIsDisplayed()
    }

    @Test
    fun homeEnglishLtr() {
        enqueueHome("[]")
        render("en") { HomeScreen(graph, rememberNavController()) }

        assertEquals(LayoutDirection.Ltr, capturedDirection)
        compose.onNodeWithText(Bidi.isolate("Omar عمر")).assertIsDisplayed()
        compose.onNodeWithText("0 / 1 done").assertIsDisplayed()
        compose.onNodeWithText("No notes yet").assertIsDisplayed()
    }

    @Test
    fun recipientsArabicRtl() {
        enqueueList()
        render("ar") { RecipientsScreen(graph, rememberNavController()) }

        assertEquals(LayoutDirection.Rtl, capturedDirection)
        compose.onNodeWithText("إضافة شخص").assertIsDisplayed()
        compose.onNodeWithText("Omar عمر").assertIsDisplayed()
    }

    @Test
    fun editorArabicRtl() {
        render("ar") { NoteEditorScreen("r1", graph, rememberNavController()) }

        assertEquals(LayoutDirection.Rtl, capturedDirection)
        compose.onNodeWithText("ملاحظة اليوم").assertIsDisplayed()
        compose.onNodeWithText("الحالة المزاجية").assertIsDisplayed()
        compose.onNodeWithText("حفظ الملاحظة").assertIsDisplayed()
    }

    @Test
    fun summaryArabicRtl() {
        render("ar") { SummaryPeriodScreen("r1", graph, rememberNavController()) }

        assertEquals(LayoutDirection.Rtl, capturedDirection)
        compose.onNodeWithText("ملخص ذكي").assertIsDisplayed()
        compose.onNodeWithText("إنشاء الملخص").assertIsDisplayed()
    }

    @Test
    fun plansArabicRtl() {
        enqueuePlans("[]")
        render("ar") { PlansScreen(graph, rememberNavController()) }

        assertEquals(LayoutDirection.Rtl, capturedDirection)
        compose.onNodeWithText("الخطط").assertIsDisplayed()
        compose.onNodeWithText("لا توجد خطة رعاية نشطة حالياً").assertIsDisplayed()
    }

    @Test
    fun plansEnglishLtr() {
        enqueuePlans("[]")
        render("en") { PlansScreen(graph, rememberNavController()) }

        assertEquals(LayoutDirection.Ltr, capturedDirection)
        compose.onNodeWithText("Plans").assertIsDisplayed()
    }

    private fun renderAuth(mode: AuthMode, language: String, onSwitch: () -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val localized = base.withLocale(language)
        val vm = AuthViewModel(
            mode,
            AuthRepository(
                FakeBackendApis(FakeAuthApi()),
                SettingsStore.create(localized, "test-rtl-auth"),
                TokenHolder(),
            ),
        )
        render(language) {
            val state by vm.state.collectAsState()
            AuthForm(
                state = state,
                mode = mode,
                onEmail = vm::onEmail,
                onPassword = vm::onPassword,
                onConfirm = vm::onConfirm,
                onSubmit = vm::submit,
                onSwitchMode = onSwitch,
            )
        }
    }

    private fun render(language: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val localized = base.withLocale(language)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized) {
                capturedDirection = LocalLayoutDirection.current
                WithTestOwner {
                    CaregiverTheme(content = content)
                }
            }
        }
    }

    private fun Context.withLocale(language: String): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(Locale(language))
        return createConfigurationContext(config)
    }
}
