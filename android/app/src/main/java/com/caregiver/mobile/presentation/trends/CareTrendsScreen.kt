package com.caregiver.mobile.presentation.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.caregiver.mobile.AppGraph
import androidx.navigation.NavController

@Composable
fun CareTrendsScreen(graph: AppGraph, recipientId: String, navController: NavController) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Care Trends", style = MaterialTheme.typography.titleLarge)
        Text("Recorded pain over time", style = MaterialTheme.typography.titleMedium)
        Text("No observations recorded for this date range.")
    }
}
