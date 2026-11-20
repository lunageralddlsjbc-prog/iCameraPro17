import androidx.compose.foundation.clickable
package com.lunageralddlsjbc.icamerapro17.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PhotoScorecard(score: PhotoScore, onDismiss: () -> Unit) {
    var animatedOverall by remember { mutableFloatStateOf(0f) }
    val targetOverall = score.overall.toFloat()
    
    LaunchedEffect(targetOverall) {
        kotlinx.coroutines.delay(100)
        animatedOverall = targetOverall
    }
    
    val animatedOverallInt by animateFloatAsState(targetValue = animatedOverall, label = "overall")

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1C1C1E)) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("AI PHOTO ANALYSIS", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("CLOSE", color = Color(0xFFFFD700), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onDismiss() })
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Overall Score
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${animatedOverallInt.toInt()}", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold)
                    Text("OVERALL SCORE", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Categories
            val categories = listOf(
                "Sharpness and details" to score.sharpness,
                "Face clarity" to score.faceClarity,
                "Skin texture" to score.skinTexture,
                "Lighting" to score.lighting,
                "Color accuracy" to score.colorAccuracy,
                "Background quality" to score.backgroundQuality,
                "Dynamic range" to score.dynamicRange,
                "Noise reduction" to score.noiseReduction,
                "Overall composition" to score.composition
            )
            
            categories.forEach { (name, value) ->
                ScoreRow(name, value)
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ScoreRow(name: String, value: Int) {
    val color = when {
        value >= 90 -> Color(0xFF34C759) // Green
        value >= 75 -> Color(0xFFFFD700) // Gold
        else -> Color(0xFFFF3B30) // Red
    }
    
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, color = Color.White, fontSize = 14.sp)
            Text("$value/100", color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(2.dp))) {
            Box(modifier = Modifier.fillMaxWidth(value / 100f).height(4.dp).background(color, RoundedCornerShape(2.dp)))
        }
    }
}
