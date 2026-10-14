package com.fvstexpress.driver.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.compose.material3.Text
import com.fvstexpress.driver.R
import com.fvstexpress.driver.ui.components.FvIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * task-camera.html: black viewfinder, ✕ top-left, yellow date/address stamp bottom-left and the
 * round white shutter. The photo is saved, shrunk to ~1600 px and handed back to the stop screen.
 */
@Composable
fun CameraScreen(nav: NavController, address: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var busy by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(1000) } }
    val stamp = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.CANADA).format(now)

    fun shoot() {
        if (busy || !granted) return
        busy = true
        val dir = File(context.filesDir, "pod").apply { mkdirs() }
        val file = File(dir, "photo-${UUID.randomUUID().toString().take(8)}.jpg")
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    scope.launch {
                        withContext(Dispatchers.IO) { shrinkJpeg(file) }
                        nav.previousBackStackEntry?.savedStateHandle?.set("captured_photo", file.absolutePath)
                        nav.popBackStack()
                    }
                }
                override fun onError(exception: ImageCaptureException) { busy = false }
            },
        )
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (granted) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            val view = this
                            val future = ProcessCameraProvider.getInstance(ctx)
                            future.addListener({
                                val provider = future.get()
                                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                                provider.unbindAll()
                                runCatching { provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture) }
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    "Camera preview\n", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                )
                Text(
                    "\n\nAllow camera access to take the proof photo.", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.clickable { permission.launch(Manifest.permission.CAMERA) },
                )
            }
            // .camera-close
            Box(
                Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.statusBars).padding(14.dp).size(34.dp)
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).clickable { nav.popBackStack() },
                contentAlignment = Alignment.Center,
            ) { FvIcon(R.drawable.ic_close, 18.dp, Color.White, "Cancel") }
            // .camera-stamp
            Text(
                "$stamp\n$address", Modifier.align(Alignment.BottomStart).padding(12.dp),
                color = Color(0xFFFFE07A), fontSize = 11.sp, lineHeight = 17.6.sp, fontFamily = FontFamily.Monospace,
            )
        }
        // .camera-controls + .camera-shutter
        Box(Modifier.fillMaxWidth().height(110.dp).background(Color.Black).windowInsetsPadding(WindowInsets.navigationBars), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(66.dp).clip(CircleShape).background(if (busy) Color(0xFFD8D8D8) else Color.White)
                    .border(4.dp, Color.White.copy(alpha = 0.4f), CircleShape).clickable { shoot() },
            )
        }
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() } }
    }
}

/** Downscale to max 1600 px and re-save at 80 % so uploads stay ~300–500 KB. */
private fun shrinkJpeg(file: File) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 3200 || bounds.outHeight / sample > 3200) sample *= 2
    val bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return
    val scale = 1600f / maxOf(bmp.width, bmp.height)
    val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
    FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.JPEG, 80, it) }
}
