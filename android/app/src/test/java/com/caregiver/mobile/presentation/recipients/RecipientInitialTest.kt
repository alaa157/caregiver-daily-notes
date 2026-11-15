package com.caregiver.mobile.presentation.recipients

import com.caregiver.mobile.data.recipient.RecipientNameRules
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipientInitialTest {

    @Test
    fun emojiInitialKeepsTheWholeCodePoint() {
        assertEquals("😀", RecipientNameRules.initial("😀Sam"))
    }
}
