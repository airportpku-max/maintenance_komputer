package com.example.ui.screens

import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onBarcodeDetected: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // Set up the ML Kit Barcode Scanner
    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
        BarcodeScanning.getClient(options)
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = modifier,
        update = { previewView ->
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // If onBarcodeDetected is supplied, we run ImageAnalysis
                val imageAnalysis = if (onBarcodeDetected != null) {
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(analysisExecutor, BarcodeAnalyzer(context, barcodeScanner, onBarcodeDetected))
                        }
                } else null

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    if (imageAnalysis != null) {
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } else {
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )
}

fun playBeepAndVibrate(context: android.content.Context) {
    try {
        val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100)
        toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 150)
    } catch (e: Exception) {
        e.printStackTrace()
    }
    try {
        val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        if (vibrator != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(150, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(150)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

class BarcodeAnalyzer(
    private val context: android.content.Context,
    private val barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    private val onBarcodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private var isProcessing = false
    private var lastScannedTime = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        val currentTime = System.currentTimeMillis()
        
        // Throttling: scan at most once per 800ms
        if (mediaImage != null && !isProcessing && (currentTime - lastScannedTime > 800)) {
            isProcessing = true
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        if (rawValue != null && rawValue.isNotBlank()) {
                            lastScannedTime = currentTime
                            playBeepAndVibrate(context)
                            onBarcodeDetected(rawValue)
                            break
                        }
                    }
                }
                .addOnFailureListener {
                    it.printStackTrace()
                }
                .addOnCompleteListener {
                    imageProxy.close()
                    isProcessing = false
                }
        } else {
            imageProxy.close()
        }
    }
}
