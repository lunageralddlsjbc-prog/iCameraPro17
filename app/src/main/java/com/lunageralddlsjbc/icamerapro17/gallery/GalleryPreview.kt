package com.lunageralddlsjbc.icamerapro17.gallery

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

@Composable
fun GalleryThumbnail(lastMediaPath: String?) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                lastMediaPath?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        val uri = Uri.fromFile(file)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, if (path.endsWith(".mp4")) "video/mp4" else "image/jpeg")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                }
            },
        colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
    ) {
        if (lastMediaPath != null) {
            AsyncImage(
                model = File(lastMediaPath),
                contentDescription = "Last Capture",
                modifier = Modifier.size(60.dp),
                contentScale = ContentScale.Crop
            )
        }
    }
}
