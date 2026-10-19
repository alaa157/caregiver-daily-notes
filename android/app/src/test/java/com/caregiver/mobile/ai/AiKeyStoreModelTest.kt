package com.caregiver.mobile.ai

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AiKeyStoreModelTest {
    private val store = AiKeyStore(ApplicationProvider.getApplicationContext())

    @Test
    fun noSavedModelReturnsNull() {
        assertNull(store.model())
    }

    @Test
    fun savedEmptyReturnsNull() {
        store.setModel("")
        assertNull(store.model())
    }

    @Test
    fun savedWhitespaceReturnsNull() {
        store.setModel("   ")
        assertNull(store.model())
    }

    @Test
    fun savedCustomReturnsModel() {
        store.setModel("custom-model")
        assertEquals("custom-model", store.model())
    }
}
