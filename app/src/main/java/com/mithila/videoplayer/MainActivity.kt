package com.mithila.videoplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MithilaPlayerApp()
        }
    }
}

@Composable
fun MithilaPlayerApp() {
    val context = LocalContext.current
    val midnightCharcoal = Color(0xFF121417)
    val champagneGold = Color(0xFFD4AF37)
    val electricTeal = Color(0xFF00E5FF)

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedVideoUri = it
            exoPlayer.setMediaItem(MediaItem.fromUri(it))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = midnightCharcoal
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "4K Video Player",
                color = champagneGold,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Mithila Stream Studio Birpur",
                color = electricTeal,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (selectedVideoUri != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "Select a 4K / HD Video to Play",
                        color = Color.LightGray,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { videoPickerLauncher.launch("video/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = champagneGold),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = midnightCharcoal)
                    Spacer(Modifier.width(8.dp))
                    Text("Pick Video", color = midnightCharcoal, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = electricTeal),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = midnightCharcoal)
                    Spacer(Modifier.width(8.dp))
                    Text("YouTube", color = midnightCharcoal, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
