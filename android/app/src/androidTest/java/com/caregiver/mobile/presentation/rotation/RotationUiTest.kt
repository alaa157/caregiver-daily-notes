package com.caregiver.mobile.presentation.rotation

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.presentation.home.HomeScreen
import com.caregiver.mobile.test.WithTestOwner
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Safety-banner retention across recreation: killing the composition owner
 * (the rotation equivalent — VMs die, disk state survives) refetches and
 * shows the fall banner again, with no dismiss control at any point.
 * Assumes an English-locale device. Instrumentation-only.
 */
@RunWith(AndroidJUnit4::class)
class RotationUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var server: MockWebServer
    private lateinit var graph: AppGraph
    private val generation = MutableStateFlow(0)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        graph = AppGraph(context)
        runBlocking {
            graph.settings.setBaseUrl(server.url("/").toString())
            graph.demoNotes.resetToSeed()
        }
        val today = LocalDate.now().toString()
        val recipients = """[{"id":"r1","name":"Ferial","active":true}]"""
        val notes = """[{"id":"n1","recipientId":"r1","date":"$today","mood":"good","appetite":"good","sleep":"ok","mobility":"walks","medicationTaken":"taken","pain":1,"fall":true,"text":"t"}]"""
        repeat(2) {
            server.enqueue(MockResponse().setBody(recipients))
            server.enqueue(MockResponse().setBody(notes))
        }
        compose.setContent {
            val gen by generation.collectAsState()
            key(gen) {
                WithTestOwner {
                    CaregiverTheme {
                        HomeScreen(graph, rememberNavController())
                    }
                }
            }
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun fallBannerPresentBeforeAndAfterRecreation() {
        compose.onAllNodesWithText("A fall was recorded. Consider contacting a doctor.").assertCountEquals(2)

        generation.value = 1
        compose.waitForIdle()

        compose.onAllNodesWithText("A fall was recorded. Consider contacting a doctor.").assertCountEquals(2)
        // No dismiss control exists anywhere in the banner tree.
        compose.onAllNodesWithText("Close").assertCountEquals(0)
        compose.onAllNodesWithText("Dismiss").assertCountEquals(0)
    }
}
