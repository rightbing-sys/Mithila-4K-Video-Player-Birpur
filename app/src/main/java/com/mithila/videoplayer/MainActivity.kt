package com.mithila.videoplayer

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.math.roundToInt

data class VideoItem(
    val id: Long,
    val title: String,
    val uri: Uri,
    val durationMs: Long
)

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
    val activity = context as? Activity
    val midnightCharcoal = Color(0xFF121417)
    val cardDark = Color(0xFF1C2026)
    val champagneGold = Color(0xFFD4AF37)
    val electricTeal = Color(0xFF00E5FF)

    val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, requiredPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    var videoList by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var currentTitle by remember { mutableStateOf("No Video Selected") }
    var isFullscreen by remember { mutableStateOf(false) }

    // Gesture overlay states
    var gestureIndicatorText by remember { mutableStateOf<String?>(null) }
    var currentBrightness by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
        )
    }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            videoList = loadDeviceVideos(context)
        }
    }

    // Handle Fullscreen System Bars & Orientation
    LaunchedEffect(isFullscreen) {
        activity?.let { act ->
            val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
            if (isFullscreen) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            videoList = loadDeviceVideos(context)
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedVideoUri = it
            currentTitle = it.lastPathSegment ?: "Picked Video"
            exoPlayer.setMediaItem(MediaItem.fromUri(it))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    fun playVideo(item: VideoItem) {
        selectedVideoUri = item.uri
        currentTitle = item.title
        exoPlayer.setMediaItem(MediaItem.fromUri(item.uri))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = midnightCharcoal
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullscreen) 0.dp else 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isFullscreen) {
                Text(
                    text = "4K Video Player",
                    color = champagneGold,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Mithila Stream Studio Birpur",
                    color = electricTeal,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            // Video Player Box with Left-Brightness & Right-Volume Vertical Swipe
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isFullscreen) Modifier.fillMaxHeight() else Modifier.height(230.dp))
                    .background(Color.Black, RoundedCornerShape(if (isFullscreen) 0.dp else 12.dp)),
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

                    // Left Half: Brightness Swipe | Right Half: Volume Swipe
                    Row(modifier = Modifier.fillMaxSize().padding(vertical = 36.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragEnd = { gestureIndicatorText = null },
                                        onDragCancel = { gestureIndicatorText = null }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        val delta = -dragAmount / 600f
                                        currentBrightness = (currentBrightness + delta).coerceIn(0.05f, 1.0f)
                                        activity?.window?.let { win ->
                                            val lp: WindowManager.LayoutParams = win.attributes
                                            lp.screenBrightness = currentBrightness
                                            win.attributes = lp
                                        }
                                        val pct = (currentBrightness * 100).roundToInt()
                                        gestureIndicatorText = "☀ Brightness: $pct%"
                                    }
                                }
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragEnd = { gestureIndicatorText = null },
                                        onDragCancel = { gestureIndicatorText = null }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        if (dragAmount < -18f && curVol < maxVol) {
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, curVol + 1, 0)
                                        } else if (dragAmount > 18f && curVol > 0) {
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, curVol - 1, 0)
                                        }
                                        val updatedVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        val pct = ((updatedVol.toFloat() / maxVol) * 100).roundToInt()
                                        gestureIndicatorText = "🔊 Volume: $pct%"
                                    }
                                }
                        )
                    }

                    // Fullscreen Toggle Button (Top Right)
                    IconButton(
                        onClick = { isFullscreen = !isFullscreen },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = champagneGold
                        )
                    }

                    // Gesture HUD Badge
                    gestureIndicatorText?.let { hudText ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = hudText,
                                color = electricTeal,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Select a Video Below or Pick File",
                        color = Color.LightGray,
                        fontSize = 15.sp
                    )
                }
            }

            if (!isFullscreen) {
                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { videoPickerLauncher.launch("video/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = champagneGold),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = midnightCharcoal)
                        Spacer(Modifier.width(6.dp))
                        Text("Pick File", color = midnightCharcoal, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = electricTeal),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = midnightCharcoal)
                        Spacer(Modifier.width(6.dp))
                        Text("YouTube", color = midnightCharcoal, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Device Video List Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Device Videos (${videoList.size})",
                        color = champagneGold,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!hasPermission) {
                        TextButton(onClick = { permissionLauncher.launch(requiredPermission) }) {
                            Text("Grant Storage Permission", color = electricTeal)
                        }
                    } else {
                        IconButton(onClick = { videoList = loadDeviceVideos(context) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = electricTeal)
                        }
                    }
                }

                // Scrollable Video List
                if (!hasPermission) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Button(
                            onClick = { permissionLauncher.launch(requiredPermission) },
                            colors = ButtonDefaults.buttonColors(containerColor = electricTeal)
                        ) {
                            Text("Load Phone Videos", color = midnightCharcoal, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (videoList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No videos found on device", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(videoList) { video ->
                            val isPlaying = video.uri == selectedVideoUri
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isPlaying) champagneGold.copy(alpha = 0.18f) else cardDark,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { playVideo(video) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = if (isPlaying) champagneGold else electricTeal,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = video.title,
                                        color = if (isPlaying) champagneGold else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatDuration(video.durationMs),
                                        color = Color.LightGray,
                                        fontSize = 12.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.PlayCircleFilled,
                                    contentDescription = "Play",
                                    tint = champagneGold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun loadDeviceVideos(context: Context): List<VideoItem> {
    val list = mutableListOf<VideoItem>()
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION
    )
    val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

    context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val name = cursor.getString(nameCol) ?: "Unknown Video"
            val dur = cursor.getLong(durCol)
            val contentUri = ContentUris.withAppendedId(collection, id)
            list.add(VideoItem(id, name, contentUri, dur))
        }
    }
    return list
}

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}
