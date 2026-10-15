package com.caregiver.mobile.presentation.home

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.theme.CaregiverTheme
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.caregiver.mobile.test.WithTestOwner

/**
 * Task 4 home behavior in Arabic against a fake server: translated greeting,
 * progress, chips, enum values, and the fall-only banner — with no raw
 * English enum text leaking into the UI. Instrumentation-only.
 */
@RunWith(AndroidJUnit4::class)
class HomeUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var server: MockWebServer
    private lateinit var graph: AppGraph

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        graph = AppGraph(context, forceProduction = true)
        runBlocking {
            graph.settings.setBaseUrl(server.url("/").toString())
            graph.settings.setEmail("layla@example.com")
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun arabicHomeRendersTranslatedContentAndFallBanner() {
        val today = LocalDate.now().toString()
        val older = LocalDate.now().minusDays(2).toString()
        server.enqueue(
            MockResponse().setBody(
                """[{"id":"r1","name":"ليلى Layla","active":true}]""",
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """[{"id":"n1","recipientId":"r1","date":"$today","mood":"good","appetite":"good","sleep":"broken","mobility":"walks","medicationTaken":"taken","pain":1,"fall":false,"text":"t"},
                    {"id":"n2","recipientId":"r1","date":"$older","mood":"bad","appetite":"poor","sleep":"poor","mobility":"bed","medicationTaken":"missed","pain":2,"fall":true,"text":"t"}]"""
                    .replace("\n", ""),
            ),
        )
        val base = ApplicationProvider.getApplicationContext<Context>()
        val localized = base.withLocale("ar")
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized) {
                WithTestOwner {
                    CaregiverTheme {
                        HomeScreen(graph, rememberNavController())
                    }
                }
            }
        }

        compose.onNodeWithText("1 / 1 مكتملة").assertIsDisplayed()
        compose.onNodeWithText("تم اليوم").assertIsDisplayed()
        compose.onNodeWithText("آخر ملاحظة: جيدة · متقطع").assertIsDisplayed()
        compose.onNodeWithText(Bidi.isolate("ليلى Layla")).assertIsDisplayed()
        compose.onNodeWithText("تنبيه سقوط").assertIsDisplayed()
        compose.onNodeWithText("سقوط مبلغ عنه — " + Bidi.isolate("ليلى Layla")).assertIsDisplayed()
        compose.onAllNodesWithText("good").assertCountEquals(0)
        compose.onAllNodesWithText("broken").assertCountEquals(0)
    }

    @Test
    fun emptyHistoryShowsEmptyState() {
        server.enqueue(MockResponse().setBody("[]"))
        server.enqueue(MockResponse().setBody("[]"))
        val base = ApplicationProvider.getApplicationContext<Context>()
        val localized = base.withLocale("en")
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized) {
                WithTestOwner {
                    CaregiverTheme {
                        HomeScreen(graph, rememberNavController())
                    }
                }
            }
        }

        compose.onNodeWithText("No people yet. Add your first person.").assertIsDisplayed()
        compose.onNodeWithText("Add person").assertIsDisplayed()
    }

    @Test
    fun failedLoadShowsRetryAndRecovers() {
        val today = LocalDate.now().toString()
        server.enqueue(
            MockResponse().setBody("""[{"id":"r1","name":"Sara","active":true}]"""),
        )
        server.enqueue(MockResponse().setResponseCode(500))
        val base = ApplicationProvider.getApplicationContext<Context>()
        val localized = base.withLocale("en")
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized) {
                WithTestOwner {
                    CaregiverTheme {
                        HomeScreen(graph, rememberNavController())
                    }
                }
            }
        }

        compose.onNodeWithText("Could not load").assertIsDisplayed()
        server.enqueue(
            MockResponse().setBody("""[{"id":"r1","name":"Sara","active":true}]"""),
        )
        server.enqueue(
            MockResponse().setBody(
                """[{"id":"n1","recipientId":"r1","date":"$today","mood":"good","appetite":"good","sleep":"good","mobility":"walks","medicationTaken":"taken","pain":1,"fall":false,"text":"t"}]""",
            ),
        )
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Sara").assertIsDisplayed()
    }

    private fun Context.withLocale(language: String): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(Locale(language))
        return createConfigurationContext(config)
    }

    @Test
    fun refreshFailureKeepsSafetyBannerVisible() {
        val today = LocalDate.now().toString()
        val older = LocalDate.now().minusDays(2).toString()
        server.enqueue(
            MockResponse().setBody("""[{"id":"r1","name":"Sara","active":true}]"""),
        )
        server.enqueue(
            MockResponse().setBody(
                """[{"id":"n1","recipientId":"r1","date":"$today","mood":"good","appetite":"good","sleep":"good","mobility":"walks","medicationTaken":"taken","pain":1,"fall":false,"text":"t"},
                    {"id":"n2","recipientId":"r1","date":"$older","mood":"bad","appetite":"poor","sleep":"poor","mobility":"bed","medicationTaken":"missed","pain":8,"fall":true,"text":"t"}]"""
                    .replace("\n", ""),
            ),
        )
        // Explicit owner (same pattern as WithTestOwner) so the test can
        // reach the screen's keyed ViewModel and trigger a refresh; the
        // factory is never invoked because the "home" instance exists.
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        val base = ApplicationProvider.getApplicationContext<Context>()
        val localized = base.withLocale("en")
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalViewModelStoreOwner provides owner,
            ) {
                CaregiverTheme {
                    HomeScreen(graph, rememberNavController())
                }
            }
        }

        // Initial load succeeds with a fall flag: banner visible.
        compose.onNodeWithText("Fall alert").assertIsDisplayed()
        compose.onNodeWithText("Reported fall — " + Bidi.isolate("Sara")).assertIsDisplayed()

        // Refresh fails: error/retry appear, but the flag/banner stays.
        server.enqueue(
            MockResponse().setBody("""[{"id":"r1","name":"Sara","active":true}]"""),
        )
        server.enqueue(MockResponse().setResponseCode(500))
        ViewModelProvider(owner)["home", HomeViewModel::class.java].refresh()
        compose.onNodeWithText("Could not load").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed()
        compose.onNodeWithText("Fall alert").assertIsDisplayed()
        compose.onNodeWithText("Reported fall — " + Bidi.isolate("Sara")).assertIsDisplayed()
    }
}
