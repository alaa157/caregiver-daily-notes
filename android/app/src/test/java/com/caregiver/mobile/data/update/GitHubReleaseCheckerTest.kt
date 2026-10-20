package com.caregiver.mobile.data.update

import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitHubReleaseCheckerTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun checker(currentVersion: String = "0.3.4") = GitHubReleaseChecker(
        currentVersion = currentVersion,
        client = OkHttpClient(),
        endpoint = server.url("/releases?per_page=30"),
    )

    private fun enqueue(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body),
        )
    }

    @Test
    fun choosesTheHighestReleaseThatShipsAnApk() = runTest {
        enqueue(
            """
            [
              {
                "tag_name": "android-0.3.9-demo",
                "html_url": "https://github.com/alaa157/caregiver-daily-notes/releases/tag/android-0.3.9-demo",
                "assets": [
                  {
                    "name": "app-minified.apk",
                    "browser_download_url": "https://github.com/alaa157/caregiver-daily-notes/releases/download/android-0.3.9-demo/app-minified.apk"
                  }
                ]
              },
              {
                "tag_name": "android-0.3.10-demo",
                "prerelease": true,
                "html_url": "https://github.com/alaa157/caregiver-daily-notes/releases/tag/android-0.3.10-demo",
                "assets": [
                  {
                    "name": "app-minified.apk",
                    "browser_download_url": "https://github.com/alaa157/caregiver-daily-notes/releases/download/android-0.3.10-demo/app-minified.apk"
                  }
                ]
              }
            ]
            """.trimIndent(),
        )

        val result = checker(currentVersion = "0.3.9").check()

        assertEquals(
            UpdateResult.Available(
                AvailableUpdate(
                    version = "0.3.10",
                    releaseUrl = "https://github.com/alaa157/caregiver-daily-notes/releases/tag/android-0.3.10-demo",
                    downloadUrl = "https://github.com/alaa157/caregiver-daily-notes/releases/download/android-0.3.10-demo/app-minified.apk",
                ),
            ),
            result,
        )
        val request = server.takeRequest()
        assertEquals("/releases?per_page=30", request.path)
        assertTrue(request.getHeader("User-Agent")!!.startsWith("CaregiverDailyNotes/"))
    }

    @Test
    fun returnsUpToDateWhenTheHighestReleaseMatchesTheInstalledVersion() = runTest {
        enqueue(
            """
            [
              {
                "tag_name": "android-0.3.4-demo",
                "html_url": "https://github.com/alaa157/caregiver-daily-notes/releases/tag/android-0.3.4-demo",
                "assets": [
                  {
                    "name": "app-minified.apk",
                    "browser_download_url": "https://github.com/alaa157/caregiver-daily-notes/releases/download/android-0.3.4-demo/app-minified.apk"
                  }
                ]
              }
            ]
            """.trimIndent(),
        )

        assertEquals(UpdateResult.UpToDate, checker().check())
    }

    @Test
    fun skipsReleasesWithoutAnApkAndUrlsOutsideTheReleasePath() = runTest {
        enqueue(
            """
            [
              {
                "tag_name": "android-9.9.9-demo",
                "html_url": "https://github.com/alaa157/caregiver-daily-notes/releases/tag/android-9.9.9-demo",
                "assets": []
              },
              {
                "tag_name": "android-9.9.8-demo",
                "html_url": "https://github.com/alaa157/caregiver-daily-notes/releases/tag/android-9.9.8-demo",
                "assets": [
                  {
                    "name": "app-minified.apk",
                    "browser_download_url": "https://example.com/app-minified.apk"
                  }
                ]
              },
              {
                "tag_name": "android-0.3.5-demo",
                "html_url": "https://github.com/alaa157/caregiver-daily-notes/releases/tag/android-0.3.5-demo",
                "assets": [
                  {
                    "name": "app-minified.apk",
                    "browser_download_url": "https://github.com/alaa157/caregiver-daily-notes/releases/download/android-0.3.5-demo/app-minified.apk"
                  }
                ]
              }
            ]
            """.trimIndent(),
        )

        val result = checker().check()

        assertEquals(
            UpdateResult.Available(
                AvailableUpdate(
                    version = "0.3.5",
                    releaseUrl = "https://github.com/alaa157/caregiver-daily-notes/releases/tag/android-0.3.5-demo",
                    downloadUrl = "https://github.com/alaa157/caregiver-daily-notes/releases/download/android-0.3.5-demo/app-minified.apk",
                ),
            ),
            result,
        )
    }

    @Test
    fun failedRequestBecomesAnIOException() = runTest {
        enqueue("""{"message":"rate limit exceeded"}""", code = 403)

        val failure = try {
            checker().check()
            null
        } catch (e: IOException) {
            e
        }

        assertTrue(failure != null)
        assertTrue(failure!!.message!!.contains("403"))
    }
}
