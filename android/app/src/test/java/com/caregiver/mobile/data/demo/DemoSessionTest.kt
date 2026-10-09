package com.caregiver.mobile.data.demo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Local demo session: starts null, flips on start/sign-out, stores nothing. */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoSessionTest {

    private lateinit var file: File
    private lateinit var session: DemoSession

    @Before
    fun setUp() {
        file = File.createTempFile("demo-session-test", ".preferences_pb")
        session = DemoSession(
            PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher()),
            ) { file },
        )
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun startsUnsetThenFlips() = runTest {
        assertNull(session.active.first())
        session.startDemo()
        assertEquals(true, session.active.first())
        session.signOut()
        assertEquals(false, session.active.first())
    }
}
