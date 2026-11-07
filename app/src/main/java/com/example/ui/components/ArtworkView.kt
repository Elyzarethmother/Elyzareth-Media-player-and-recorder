package com.example.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Song

@Composable
fun ArtworkView(
    currentSong: Song?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val customArtUri = currentSong?.customArtworkUri
    val customBitmap = remember(customArtUri) {
        if (customArtUri == null) {
            null
        } else {
            try {
                if (customArtUri.startsWith("file://")) {
                    val path = Uri.parse(customArtUri).path
                    if (path != null) BitmapFactory.decodeFile(path) else null
                } else {
                    context.contentResolver.openInputStream(Uri.parse(customArtUri))?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2E1C4E),
                        Color(0xFF140F22),
                        Color(0xFF0C0916)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = Color(0xFF6B4CA8).copy(alpha = 0.4f),
                shape = RoundedCornerShape(20.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (customBitmap != null) {
            Image(
                bitmap = customBitmap.asImageBitmap(),
                contentDescription = "Song Artwork for ${currentSong?.songTitle ?: "Track"}",
                contentScale = ContentScale.Fit, // Strict FIT mode as required
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .testTag("artwork_image_custom")
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.elyzareth_album_art),
                contentDescription = "Default Elyzareth Artwork",
                contentScale = ContentScale.Fit, // Strict FIT mode as required
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .testTag("artwork_image_default")
            )
        }
    }
}
