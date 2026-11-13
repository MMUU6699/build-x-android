package app.buildx.chat.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import app.buildx.chat.ui.components.MarkLoader
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.util.AvatarImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * In-app Image Crop Screen:
 * - Square output (512x512 JPEG at 88% quality)
 * - Decodes safely from raw bytes (preventing exhausted stream and hardware bitmap crashes)
 * - Circular preview mask with dimmed surround and thin white aperture outline
 * - Pan and pinch-to-zoom gestures
 * - Minimum zoom clamped to ensure crop area is always filled
 * - Pan clamped so crop box never extends outside the image
 * - Cancel and Done pill action buttons
 * - Processing indicator and friendly error messaging
 */
@Composable
fun ImageCropScreen(
    imageBytes: ByteArray? = null,
    imageUri: Uri? = null,
    onCropDone: (jpegBytes: ByteArray, cacheUri: Uri?) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isDecoding by remember { mutableStateOf(true) }
    var decodeError by remember { mutableStateOf(false) }
    var isProcessingCrop by remember { mutableStateOf(false) }

    var userScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var activeCropBoxPx by remember { mutableFloatStateOf(0f) }

    BackHandler(enabled = !isProcessingCrop) {
        onCancel()
    }

    // Decode image from rawBytes (preferred) or Uri stream safely with subsampling (<= 1440px)
    LaunchedEffect(imageBytes, imageUri) {
        withContext(Dispatchers.IO) {
            val decoded = if (imageBytes != null && imageBytes.isNotEmpty()) {
                AvatarImageProcessor.decodeSubsampledBitmap(imageBytes, maxDimension = 1440)
            } else if (imageUri != null) {
                AvatarImageProcessor.decodeSubsampledBitmap(context, imageUri, maxDimension = 1440)
            } else {
                null
            }

            withContext(Dispatchers.Main) {
                if (decoded != null) {
                    sourceBitmap = decoded
                    isDecoding = false
                } else {
                    isDecoding = false
                    decodeError = true
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1012))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Title & Instructions
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.crop_screen_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.crop_screen_subtitle),
                fontSize = 13.sp,
                color = Color(0xFF9BA0A5)
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Center Viewport: Box with constraints to size the crop window
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val availableWidth = constraints.maxWidth.toFloat()
                val availableHeight = constraints.maxHeight.toFloat()
                val cropBoxSizePx = minOf(availableWidth * 0.85f, availableHeight * 0.85f, 900f)
                activeCropBoxPx = cropBoxSizePx
                val cropBoxSizeDp = with(density) { cropBoxSizePx.toDp() }

                val bmp = sourceBitmap
                if (bmp != null) {
                    val bmpW = bmp.width.toFloat()
                    val bmpH = bmp.height.toFloat()

                    // Base scale so image fills the crop box completely
                    val baseScale = maxOf(cropBoxSizePx / bmpW, cropBoxSizePx / bmpH)
                    val totalScale = baseScale * userScale
                    val dispW = bmpW * totalScale
                    val dispH = bmpH * totalScale

                    // Clamp pan so crop box is always within image bounds
                    val maxPanX = maxOf(0f, (dispW - cropBoxSizePx) / 2f)
                    val maxPanY = maxOf(0f, (dispH - cropBoxSizePx) / 2f)
                    val clampedPanX = panOffset.x.coerceIn(-maxPanX, maxPanX)
                    val clampedPanY = panOffset.y.coerceIn(-maxPanY, maxPanY)

                    // Update panOffset if clamped changed
                    if (clampedPanX != panOffset.x || clampedPanY != panOffset.y) {
                        panOffset = Offset(clampedPanX, clampedPanY)
                    }

                    // Touch gesture layer
                    Box(
                        modifier = Modifier
                            .size(cropBoxSizeDp)
                            .clipToBounds()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (userScale * zoom).coerceIn(1f, 4f)
                                    userScale = newScale

                                    val curTotalScale = baseScale * newScale
                                    val curDispW = bmpW * curTotalScale
                                    val curDispH = bmpH * curTotalScale

                                    val curMaxPanX = maxOf(0f, (curDispW - cropBoxSizePx) / 2f)
                                    val curMaxPanY = maxOf(0f, (curDispH - cropBoxSizePx) / 2f)

                                    val newPanX = (panOffset.x + pan.x).coerceIn(-curMaxPanX, curMaxPanX)
                                    val newPanY = (panOffset.y + pan.y).coerceIn(-curMaxPanY, curMaxPanY)
                                    panOffset = Offset(newPanX, newPanY)
                                }
                            }
                    ) {
                        // Image layer with transform
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = totalScale
                                    scaleY = totalScale
                                    translationX = panOffset.x
                                    translationY = panOffset.y
                                }
                        )

                        // Circular aperture preview mask
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val radius = minOf(w, h) / 2f
                            val center = Offset(w / 2f, h / 2f)

                            val fullRectPath = Path().apply {
                                addRect(Rect(0f, 0f, w, h))
                            }
                            val circleHolePath = Path().apply {
                                addOval(
                                    Rect(
                                        left = center.x - radius,
                                        top = center.y - radius,
                                        right = center.x + radius,
                                        bottom = center.y + radius
                                    )
                                )
                            }
                            val dimmedMaskPath = Path.combine(
                                operation = PathOperation.Difference,
                                path1 = fullRectPath,
                                path2 = circleHolePath
                            )

                            // Dimmed surround
                            drawPath(
                                path = dimmedMaskPath,
                                color = Color.Black.copy(alpha = 0.65f)
                            )

                            // Crisp aperture outline ring
                            drawCircle(
                                color = Color.White.copy(alpha = 0.9f),
                                radius = radius - 1f,
                                center = center,
                                style = Stroke(width = 1.8f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons: Cancel and Done pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel Pill
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22262B))
                        .clickable(enabled = !isProcessingCrop) { onCancel() }
                        .padding(horizontal = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.crop_screen_cancel),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                // Done Pill
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0063D3))
                        .clickable(enabled = !isProcessingCrop && sourceBitmap != null) {
                            val bmp = sourceBitmap ?: return@clickable
                            val cropBoxPx = if (activeCropBoxPx > 0f) activeCropBoxPx else 800f
                            isProcessingCrop = true
                            coroutineScope.launch(Dispatchers.Default) {
                                try {
                                    val baseScale = maxOf(cropBoxPx / bmp.width.toFloat(), cropBoxPx / bmp.height.toFloat())
                                    val totalScale = baseScale * userScale
                                    val dispW = bmp.width * totalScale
                                    val dispH = bmp.height * totalScale

                                    val cropLeftDisp = (dispW - cropBoxPx) / 2f - panOffset.x
                                    val cropTopDisp = (dispH - cropBoxPx) / 2f - panOffset.y

                                    val bmpCropLeft = (cropLeftDisp / totalScale).toInt().coerceIn(0, maxOf(0, bmp.width - 1))
                                    val bmpCropTop = (cropTopDisp / totalScale).toInt().coerceIn(0, maxOf(0, bmp.height - 1))
                                    val maxAvailable = minOf(bmp.width - bmpCropLeft, bmp.height - bmpCropTop)
                                    val bmpCropSize = (cropBoxPx / totalScale).toInt().coerceIn(1, maxAvailable)

                                    val jpegBytes = AvatarImageProcessor.cropAndCompressAvatar(
                                        source = bmp,
                                        cropLeft = bmpCropLeft,
                                        cropTop = bmpCropTop,
                                        cropSize = bmpCropSize,
                                        targetSize = 512,
                                        quality = 88
                                    )
                                    val cacheUri = AvatarImageProcessor.saveToTempCache(context, jpegBytes)

                                    withContext(Dispatchers.Main) {
                                        onCropDone(jpegBytes, cacheUri)
                                    }
                                } catch (_: Throwable) {
                                    withContext(Dispatchers.Main) {
                                        decodeError = true
                                        isProcessingCrop = false
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.crop_screen_done),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Processing / Loading Overlay
        if (isDecoding || isProcessingCrop) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MarkLoader(
                        size = 36.dp,
                        isSpinning = true,
                        tint = Color(0xFF0063D3)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.crop_screen_processing),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }

        // Friendly Error Dialog
        if (decodeError) {
            AlertDialog(
                onDismissRequest = { onCancel() },
                title = {
                    Text(
                        text = stringResource(R.string.crop_screen_error_title),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.crop_screen_error_desc),
                        fontSize = 14.sp,
                        color = Color(0xFF9BA0A5)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { onCancel() }) {
                        Text(
                            text = stringResource(R.string.crop_screen_cancel),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0063D3)
                        )
                    }
                },
                containerColor = Color(0xFF171A1E),
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}
