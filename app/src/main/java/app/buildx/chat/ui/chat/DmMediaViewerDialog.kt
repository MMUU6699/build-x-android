package app.buildx.chat.ui.chat

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import app.buildx.chat.R
import app.buildx.chat.data.model.DmMessage
import app.buildx.chat.ui.theme.BuildXFontFamily
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Full-screen interactive media viewer for DM & Group chat images:
 * - Pinch-to-zoom (1x to 4x), double-tap zoom, and clamped pan
 * - Single tap toggles the top bar cleanly
 * - Working Share (cached FileProvider URI) and Save to Gallery (MediaStore / DownloadManager)
 */
@Composable
fun DmMediaViewerDialog(
    message: DmMessage,
    onDismiss: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val imageUrl = message.attachmentPath ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val enterScale = remember { Animatable(0.92f) }
    val enterAlpha = remember { Animatable(0f) }
    LaunchedEffect(message.id) {
        launch {
            enterAlpha.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        }
        launch {
            enterScale.animateTo(1f, tween(210, easing = FastOutSlowInEasing))
        }
    }

    var zoomScale by remember(message.id) { mutableFloatStateOf(1f) }
    var panOffset by remember(message.id) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var showOverlay by remember(message.id) { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    val formattedTime = remember(message.createdAt) {
        formatViewerTimestamp(message.createdAt)
    }
    val senderName = message.senderDisplayName?.takeIf { it.isNotBlank() }
        ?: message.senderUsername?.let { "@$it" }
        ?: stringResource(R.string.dm_you)

    fun clampOffset(candidate: Offset, targetScale: Float): Offset {
        if (targetScale <= 1.01f || containerSize == IntSize.Zero) return Offset.Zero
        val maxX = (containerSize.width * (targetScale - 1f)) / 2f
        val maxY = (containerSize.height * (targetScale - 1f)) / 2f
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY)
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f * enterAlpha.value))
                .onSizeChanged { containerSize = it }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(message.id) {
                        detectTapGestures(
                            onTap = { showOverlay = !showOverlay },
                            onDoubleTap = { tapOffset ->
                                if (zoomScale > 1.05f) {
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                } else {
                                    val nextScale = 2.5f
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val deltaFromCenter = tapOffset - center
                                    zoomScale = nextScale
                                    panOffset = clampOffset(-deltaFromCenter * (nextScale - 1f), nextScale)
                                }
                            }
                        )
                    }
                    .pointerInput(message.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (zoomScale * zoom).coerceIn(1f, 4f)
                            val updatedOffset = if (newScale <= 1.01f) {
                                Offset.Zero
                            } else {
                                clampOffset(panOffset + pan * newScale, newScale)
                            }
                            zoomScale = newScale
                            panOffset = updatedOffset
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = message.content.takeIf { it.isNotBlank() },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val combinedScale = enterScale.value * zoomScale
                            scaleX = combinedScale
                            scaleY = combinedScale
                            translationX = panOffset.x
                            translationY = panOffset.y
                            alpha = enterAlpha.value
                        }
                )
            }

            AnimatedVisibility(
                visible = showOverlay,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.72f),
                                1f to Color.Transparent
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ViewerOverlayCircleButton(
                        iconRes = R.drawable.ic_close,
                        contentDescription = stringResource(R.string.dm_viewer_close),
                        onClick = onDismiss
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = senderName,
                            color = Color.White,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = BuildXFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (formattedTime.isNotBlank()) {
                            Text(
                                text = formattedTime,
                                color = Color.White.copy(alpha = 0.74f),
                                fontSize = 12.sp,
                                fontFamily = BuildXFontFamily,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    ViewerOverlayCircleButton(
                        iconRes = R.drawable.ic_share,
                        contentDescription = stringResource(R.string.action_share),
                        onClick = {
                            scope.launch {
                                shareImageFromViewer(context, imageUrl)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    ViewerOverlayCircleButton(
                        iconRes = R.drawable.ic_download,
                        contentDescription = stringResource(R.string.dm_save_to_gallery),
                        isLoading = isSaving,
                        onClick = {
                            if (isSaving) return@ViewerOverlayCircleButton
                            scope.launch {
                                isSaving = true
                                val ok = saveImageToGallery(context, imageUrl)
                                isSaving = false
                                onShowMessage(
                                    context.getString(
                                        if (ok) R.string.dm_saved_to_gallery else R.string.dm_save_failed
                                    )
                                )
                            }
                        }
                    )
                }
            }

            if (message.content.isNotBlank()) {
                AnimatedVisibility(
                    visible = showOverlay,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(150)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.78f)
                                )
                            )
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        Text(
                            text = message.content,
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontFamily = BuildXFontFamily,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerOverlayCircleButton(
    iconRes: Int,
    contentDescription: String,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(enabled = !isLoading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

private fun formatViewerTimestamp(createdAt: String): String {
    if (createdAt.isBlank()) return ""
    return runCatching {
        val zoned = Instant.parse(createdAt).atZone(ZoneId.systemDefault())
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .format(zoned)
    }.getOrElse { createdAt.take(16) }
}

private suspend fun openImageInputStream(context: Context, source: String): InputStream? {
    return if (source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true)) {
        URL(source).openStream()
    } else {
        context.contentResolver.openInputStream(Uri.parse(source))
    }
}

private suspend fun shareImageFromViewer(context: Context, imageUrl: String) {
    val sharedUri: Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val sharedDir = File(context.cacheDir, "shared_media").apply { mkdirs() }
            val outFile = File(sharedDir, "buildx_share_${System.currentTimeMillis()}.jpg")
            openImageInputStream(context, imageUrl)?.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        }.getOrNull()
    }

    val intent = if (sharedUri != null) {
        Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, sharedUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, imageUrl)
        }
    }
    context.startActivity(Intent.createChooser(intent, null))
}

private suspend fun saveImageToGallery(context: Context, imageUrl: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val fileName = "BuildX_${System.currentTimeMillis()}.jpg"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Build X")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val destUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching false
                try {
                    openImageInputStream(context, imageUrl)?.use { input ->
                        resolver.openOutputStream(destUri)?.use { output ->
                            input.copyTo(output)
                        } ?: throw IllegalStateException("Could not open gallery output stream")
                    } ?: throw IllegalStateException("Could not read source image")
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(destUri, values, null, null)
                    true
                } catch (e: Exception) {
                    resolver.delete(destUri, null, null)
                    false
                }
            } else if (imageUrl.startsWith("http://", ignoreCase = true) ||
                imageUrl.startsWith("https://", ignoreCase = true)
            ) {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                    ?: return@runCatching false
                val req = DownloadManager.Request(Uri.parse(imageUrl))
                    .setTitle(fileName)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "Build X/$fileName")
                dm.enqueue(req)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }
