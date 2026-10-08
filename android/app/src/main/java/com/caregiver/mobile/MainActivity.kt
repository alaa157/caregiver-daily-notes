package com.caregiver.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.caregiver.mobile.core.theme.CaregiverTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CaregiverTheme {
                CaregiverRoot((application as CaregiverApp).graph)
            }
        }
    }
}
