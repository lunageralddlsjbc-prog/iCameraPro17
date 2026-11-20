package com.lunageralddlsjbc.icamerapro17.gallery

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
fun GalleryThumbnail(lastMediaPath: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    
    Card(
        modifier = modifier
            .size(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                lastMediaPath?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        // FIXED: Open the specific last photo, not the whole gallery
                        val uri = Uri.fromFile(file)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, if (path.endsWith(".mp4")) "video/mp4" else "image/jpeg")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } else {
                        // Fallback: open gallery app if no photo yet
                        val galleryIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_APP_GALLERY)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        if (galleryIntent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(galleryIntent)
                        }
                    }
                }
            },
        colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
    ) {
        Box(modifier = Modifier.size(60.dp).background(Color.Black), contentAlignment = Alignment.Center) {
            Text(text = if (lastMediaPath != null) "🖼️" else "📷", fontSize = 24.sp)
        }
    }
}
