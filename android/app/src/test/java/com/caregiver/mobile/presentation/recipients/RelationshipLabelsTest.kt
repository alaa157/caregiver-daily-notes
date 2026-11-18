package com.caregiver.mobile.presentation.recipients

import com.caregiver.mobile.data.recipient.RelationshipOptions
import org.junit.Assert.assertNotNull
import org.junit.Test

class RelationshipLabelsTest {

    @Test
    fun everyCodeHasALabelResource() {
        RelationshipOptions.codes.forEach { code ->
            assertNotNull("missing label for $code", relationshipLabelRes(code))
        }
    }

    @Test
    fun unknownCodesHaveNoLabelResource() {
        org.junit.Assert.assertNull(relationshipLabelRes("AUNT"))
        org.junit.Assert.assertNull(relationshipLabelRes(""))
    }
}
