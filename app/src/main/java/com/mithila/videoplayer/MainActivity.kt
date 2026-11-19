package com.mithila.videoplayer

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
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
            MithilaStudioPremiumApp()
        }
    }
}

@Composable
fun MithilaStudioPremiumApp() {
    val midnightCharcoal = Color(0xFF121417)
    val cardDark = Color(0xFF1C2026)
    val champagneGold = Color(0xFFD4AF37)
    val electricTeal = Color(0xFF00E5FF)

    var selectedTab by remember { mutableIntStateOf(0) }
    var isFullscreen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = midnightCharcoal,
        bottomBar = {
            if (!isFullscreen) {
                NavigationBar(containerColor = cardDark) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.PlayCircleFilled, contentDescription = "Player") },
                        label = { Text("4K Player") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = midnightCharcoal,
                            selectedTextColor = champagneGold,
                            indicatorColor = champagneGold,
                            unselectedIconColor = Color.LightGray,
                            unselectedTextColor = Color.LightGray
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Download, contentDescription = "Downloader") },
                        label = { Text("Link Saver") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = midnightCharcoal,
                            selectedTextColor = electricTeal,
                            indicatorColor = electricTeal,
                            unselectedIconColor = Color.LightGray,
                            unselectedTextColor = Color.LightGray
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.AutoFixHigh, contentDescription = "AI Editor") },
                        label = { Text("AI Pics") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = midnightCharcoal,
                            selectedTextColor = champagneGold,
                            indicatorColor = champagneGold,
                            unselectedIconColor = Color.LightGray,
                            unselectedTextColor = Color.LightGray
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (selectedTab) {
                0 -> VideoPlayerTab(
                    isFullscreen = isFullscreen,
                    onFullscreenChange = { isFullscreen = it }
                )
                1 -> LinkDownloaderTab()
                2 -> AiPhotoEditorTab()
            }
        }
    }
}

@Composable
fun VideoPlayerTab(
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit
) {
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
    var gestureIndicatorText by remember { mutableStateOf<String?>(null) }
    var currentBrightness by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
        )
    }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            videoList = loadDeviceVideos(context)
        }
    }

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
        if (granted) videoList = loadDeviceVideos(context)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(midnightCharcoal)
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isFullscreen) Modifier.fillMaxHeight() else Modifier.height(220.dp))
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
                                    gestureIndicatorText = "☀ Brightness: ${(currentBrightness * 100).roundToInt()}%"
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
                                    gestureIndicatorText = "🔊 Volume: ${((updatedVol.toFloat() / maxVol) * 100).roundToInt()}%"
                                }
                            }
                    )
                }

                IconButton(
                    onClick = { onFullscreenChange(!isFullscreen) },
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

                gestureIndicatorText?.let { hudText ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(text = hudText, color = electricTeal, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Text(text = "Select a Video Below or Pick File", color = Color.LightGray, fontSize = 15.sp)
            }
        }

        if (!isFullscreen) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { videoPickerLauncher.launch("video/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = champagneGold),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = midnightCharcoal)
                    Spacer(Modifier.width(6.dp))
                    Text("Pick File", color = midnightCharcoal, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = electricTeal),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = midnightCharcoal)
                    Spacer(Modifier.width(6.dp))
                    Text("YouTube", color = midnightCharcoal, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Device Videos (${videoList.size})", color = champagneGold, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (!hasPermission) {
                    TextButton(onClick = { permissionLauncher.launch(requiredPermission) }) {
                        Text("Grant Permission", color = electricTeal)
                    }
                } else {
                    IconButton(onClick = { videoList = loadDeviceVideos(context) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = electricTeal)
                    }
                }
            }

            if (!hasPermission) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = { permissionLauncher.launch(requiredPermission) },
                        colors = ButtonDefaults.buttonColors(containerColor = electricTeal)
                    ) {
                        Text("Load Phone Videos", color = midnightCharcoal, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(videoList) { video ->
                        val isPlaying = video.uri == selectedVideoUri
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isPlaying) champagneGold.copy(alpha = 0.18f) else cardDark, RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedVideoUri = video.uri
                                    exoPlayer.setMediaItem(MediaItem.fromUri(video.uri))
                                    exoPlayer.prepare()
                                    exoPlayer.playWhenReady = true
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, tint = if (isPlaying) champagneGold else electricTeal, modifier = Modifier.size(30.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(video.title, color = if (isPlaying) champagneGold else Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatDuration(video.durationMs), color = Color.LightGray, fontSize = 12.sp)
                            }
                            Icon(Icons.Default.PlayCircleFilled, contentDescription = "Play", tint = champagneGold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LinkDownloaderTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val midnightCharcoal = Color(0xFF121417)
    val cardDark = Color(0xFF1C2026)
    val champagneGold = Color(0xFFD4AF37)
    val electricTeal = Color(0xFF00E5FF)

    var urlInput by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Paste any Pinterest, image, or direct MP4 video link below.") }
    var isExtracting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(midnightCharcoal)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Smart Media Link Saver", color = champagneGold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Pinterest Pics • Videos • Direct Web Media", color = electricTeal, fontSize = 13.sp, modifier = Modifier.padding(bottom = 16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = cardDark),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Paste Video / Pic URL", color = electricTeal) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = champagneGold,
                        unfocusedBorderColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!clipText.isNullOrBlank()) {
                                urlInput = clipText.trim()
                                statusText = "Link pasted from clipboard!"
                            } else {
                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = electricTeal),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = midnightCharcoal)
                        Spacer(Modifier.width(6.dp))
                        Text("Paste Link", color = midnightCharcoal, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val cleanUrl = urlInput.trim()
                            if (!cleanUrl.startsWith("http")) {
                                statusText = "Please enter a valid http/https link."
                                return@Button
                            }
                            isExtracting = true
                            statusText = "Scanning link for HD video / image..."
                            scope.launch {
                                val resolvedUrl = resolveDirectMediaUrl(cleanUrl)
                                isExtracting = false
                                if (resolvedUrl != null) {
                                    enqueueDownload(context, resolvedUrl)
                                    statusText = "Download started! Check your phone's Downloads folder."
                                } else {
                                    statusText = "Could not extract direct media from this page. Try a direct image/video link or Pinterest pin URL."
                                }
                            }
                        },
                        enabled = !isExtracting,
                        colors = ButtonDefaults.buttonColors(containerColor = champagneGold),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = midnightCharcoal)
                        Spacer(Modifier.width(6.dp))
                        Text(if (isExtracting) "Scanning..." else "Download", color = midnightCharcoal, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardDark, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Text(text = statusText, color = Color.White, fontSize = 14.sp)
        }
    }
}

suspend fun resolveDirectMediaUrl(rawUrl: String): String? = withContext(Dispatchers.IO) {
    try {
        val lower = rawUrl.lowercase()
        if (lower.endsWith(".mp4") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".png") || lower.endsWith(".webp") || lower.contains("pinimg.com")
        ) {
            return@withContext rawUrl
        }
        val conn = URL(rawUrl).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val html = conn.inputStream.bufferedReader().use { it.readText() }

        val ogVideo = Regex("""<meta[^>]+property=["']og:video(?::url|:secure_url)?["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
        if (!ogVideo.isNullOrBlank()) return@withContext ogVideo.replace("&amp;", "&")

        val mp4Match = Regex("""https?://[^"'\s]+\.mp4[^"'\s]*""", RegexOption.IGNORE_CASE).find(html)?.value
        if (!mp4Match.isNullOrBlank()) return@withContext mp4Match.replace("\\u002F", "/")

        val ogImage = Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
        if (!ogImage.isNullOrBlank()) return@withContext ogImage.replace("&amp;", "&")

        null
    } catch (e: Exception) {
        null
    }
}

fun enqueueDownload(context: Context, mediaUrl: String) {
    val isVideo = mediaUrl.lowercase().contains(".mp4") || mediaUrl.lowercase().contains("video")
    val ext = if (isVideo) "mp4" else "jpg"
    val fileName = "MithilaStudio_${System.currentTimeMillis()}.$ext"
    val request = DownloadManager.Request(Uri.parse(mediaUrl))
        .setTitle(fileName)
        .setDescription("Downloading via Mithila Stream Studio")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    dm.enqueue(request)
    Toast.makeText(context, "Downloading $fileName...", Toast.LENGTH_LONG).show()
}

@Composable
fun AiPhotoEditorTab() {
    val context = LocalContext.current
    val midnightCharcoal = Color(0xFF121417)
    val cardDark = Color(0xFF1C2026)
    val champagneGold = Color(0xFFD4AF37)
    val electricTeal = Color(0xFF00E5FF)

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var editedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var activeFilterName by remember { mutableStateOf("Original") }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                val bmp = BitmapFactory.decodeStream(stream)
                originalBitmap = bmp
                editedBitmap = bmp
                activeFilterName = "Original"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(midnightCharcoal)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("AI Smart Photo Studio", color = champagneGold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Auto-Enhance • Gold Tone • 4K Crisp Filters", color = electricTeal, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp)
                .background(cardDark, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (editedBitmap != null) {
                Image(
                    bitmap = editedBitmap!!.asImageBitmap(),
                    contentDescription = "Edited Image",
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text("Pick a Photo to AI Enhance", color = Color.LightGray)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { imagePicker.launch("image/*") },
                colors = ButtonDefaults.buttonColors(containerColor = electricTeal),
                modifier = Modifier.weight(1f).height(44.dp)
            ) {
                Icon(Icons.Default.Image, contentDescription = null, tint = midnightCharcoal)
                Spacer(Modifier.width(6.dp))
                Text("Pick Pic", color = midnightCharcoal, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    editedBitmap?.let { bmp ->
                        saveBitmapToGallery(context, bmp)
                    } ?: Toast.makeText(context, "Select an image first", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = champagneGold),
                modifier = Modifier.weight(1f).height(44.dp)
            ) {
                Icon(Icons.Default.SaveAlt, contentDescription = null, tint = midnightCharcoal)
                Spacer(Modifier.width(6.dp))
                Text("Save HD", color = midnightCharcoal, fontWeight = FontWeight.Bold)
            }
        }

        if (originalBitmap != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Text("AI Preset: $activeFilterName", color = champagneGold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChipButton("AI Enhance", Modifier.weight(1f), champagneGold, midnightCharcoal) {
                    editedBitmap = applyAiFilter(originalBitmap!!, contrast = 1.22f, brightness = 12f, saturation = 1.25f)
                    activeFilterName = "AI Auto-Enhance"
                }
                FilterChipButton("Gold Glow", Modifier.weight(1f), electricTeal, midnightCharcoal) {
                    editedBitmap = applyAiFilter(originalBitmap!!, contrast = 1.15f, brightness = 15f, saturation = 1.35f, warmTint = true)
                    activeFilterName = "Champagne Gold Glow"
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChipButton("B&W Pro", Modifier.weight(1f), electricTeal, midnightCharcoal) {
                    editedBitmap = applyAiFilter(originalBitmap!!, contrast = 1.3f, brightness = 5f, saturation = 0f)
                    activeFilterName = "Studio B&W"
                }
                FilterChipButton("Rotate 90°", Modifier.weight(1f), champagneGold, midnightCharcoal) {
                    editedBitmap = rotateBitmap(editedBitmap ?: originalBitmap!!, 90f)
                    activeFilterName = "Rotated 90°"
                }
                FilterChipButton("Reset", Modifier.weight(1f), Color.LightGray, midnightCharcoal) {
                    editedBitmap = originalBitmap
                    activeFilterName = "Original"
                }
            }
        }
    }
}

@Composable
fun FilterChipButton(label: String, modifier: Modifier, bg: Color, fg: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = bg),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        modifier = modifier.height(40.dp)
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

fun applyAiFilter(
    src: Bitmap,
    contrast: Float,
    brightness: Float,
    saturation: Float,
    warmTint: Boolean = false
): Bitmap {
    val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val satMatrix = ColorMatrix().apply { setSaturation(saturation) }
    val redBoost = if (warmTint) 1.08f else 1.0f
    val blueCut = if (warmTint) 0.92f else 1.0f
    val cbMatrix = ColorMatrix(
        floatArrayOf(
            contrast * redBoost, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast * blueCut, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        )
    )
    satMatrix.postConcat(cbMatrix)
    paint.colorFilter = ColorMatrixColorFilter(satMatrix)
    canvas.drawBitmap(src, 0f, 0f, paint)
    return result
}

fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
}

fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
    val filename = "MithilaAI_${System.currentTimeMillis()}.jpg"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MithilaStudio")
        }
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    uri?.let {
        context.contentResolver.openOutputStream(it)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        Toast.makeText(context, "Saved to Pictures/MithilaStudio/$filename", Toast.LENGTH_LONG).show()
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
