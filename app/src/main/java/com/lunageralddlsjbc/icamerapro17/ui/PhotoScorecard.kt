package com.lunageralddlsjbc.icamerapro17.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunageralddlsjbc.icamerapro17.ai.PhotoScore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoScorecard(score: PhotoScore, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1C1C1E)) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("AI PHOTO ANALYSIS", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("CLOSE", color = Color(0xFFFFD700), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onDismiss() })
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("${score.overall}", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("OVERALL SCORE", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(modifier = Modifier.height(24.dp))
            
            listOf("Sharpness" to score.sharpness, "Face Clarity" to score.faceClarity, "Lighting" to score.lighting, "Color Accuracy" to score.colorAccuracy).forEach { (name, value) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, color = Color.White, fontSize = 14.sp)
                    Text("$value/100", color = if (value >= 80) Color(0xFF34C759) else Color(0xFFFFD700), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(2.dp))) {
                    Box(modifier = Modifier.fillMaxWidth(value / 100f).height(4.dp).background(if (value >= 80) Color(0xFF34C759) else Color(0xFFFFD700), RoundedCornerShape(2.dp)))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
