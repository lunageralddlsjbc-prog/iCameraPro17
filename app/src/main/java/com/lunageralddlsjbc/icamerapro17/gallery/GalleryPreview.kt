package com.lunageralddlsjbc.icamerapro17.gallery

import android.content.Intent
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

@Composable
fun GalleryThumbnail(lastMediaPath: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    
    Card(
        modifier = modifier
            .size(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                // FIXED: Use specific Gallery App Intent instead of generic image viewer
                val galleryIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_GALLERY)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                
                // Check if a gallery app exists, otherwise fallback to generic picker
                if (galleryIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(galleryIntent)
                } else {
                    val fallbackIntent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallbackIntent)
                }
            },
        colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
    ) {
        Box(modifier = Modifier.size(60.dp).background(Color.Black), contentAlignment = Alignment.Center) {
            Text(text = "🖼️", fontSize = 24.sp)
        }
    }
}
