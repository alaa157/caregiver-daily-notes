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
        graph = AppGraph(context)
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

        compose.onAllNodesWithText("عرض الملخص").assertCountEquals(2)
        compose.onNodeWithText("تم حفظ الملاحظة").assertIsDisplayed()
        compose.onNodeWithText("تم حفظ الملاحظة: جيدة · متقطع").assertIsDisplayed()
        compose.onNodeWithText(Bidi.isolate("ليلى Layla")).assertIsDisplayed()
        compose.onAllNodesWithText("تم تسجيل حالة سقوط. يُنصح بالتواصل مع الطبيب.").assertCountEquals(2)
        compose.onAllNodesWithText("good").assertCountEquals(0)
        compose.onAllNodesWithText("broken").assertCountEquals(0)
    }

    private fun Context.withLocale(language: String): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(Locale(language))
        return createConfigurationContext(config)
    }
}
