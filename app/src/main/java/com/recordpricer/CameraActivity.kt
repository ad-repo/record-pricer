package com.recordpricer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.recordpricer.databinding.ActivityCameraBinding
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var barcodeFound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        }

        binding.previewView.setOnClickListener { takePhoto() }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            val barcodeAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    val scanner = BarcodeScanning.getClient()
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (barcodeFound) { imageProxy.close(); return@setAnalyzer }
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    val barcode = barcodes.firstOrNull {
                                        it.format == Barcode.FORMAT_EAN_13 ||
                                        it.format == Barcode.FORMAT_EAN_8 ||
                                        it.format == Barcode.FORMAT_UPC_A ||
                                        it.format == Barcode.FORMAT_UPC_E
                                    }
                                    if (barcode?.rawValue != null && !barcodeFound) {
                                        barcodeFound = true
                                        Log.d("RecordPricer", "Barcode found: ${barcode.rawValue}")
                                        returnResult(barcode = barcode.rawValue)
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,
                preview, imageCapture, barcodeAnalyzer)

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        binding.previewView.isClickable = false

        val photoFile = File(cacheDir, "record_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val croppedPath = cropToGuide(photoFile)
                    returnResult(photoPath = croppedPath)
                }
                override fun onError(e: ImageCaptureException) {
                    Log.e("RecordPricer", "Capture failed", e)
                    binding.previewView.isClickable = true
                }
            })
    }

    private fun returnResult(barcode: String? = null, photoPath: String? = null) {
        val intent = Intent().apply {
            barcode?.let { putExtra(EXTRA_BARCODE, it) }
            photoPath?.let { putExtra(EXTRA_PHOTO_PATH, it) }
        }
        setResult(RESULT_OK, intent)
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 100 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    /**
     * Crops the saved photo to match the square guide overlay.
     * The overlay draws a square = 82% of min(screenW, screenH), centered at (cx, cy-40).
     * We map those proportions onto the captured image dimensions.
     */
    private fun cropToGuide(file: File): String {
        return try {
            val raw = BitmapFactory.decodeFile(file.absolutePath)
                ?: return file.absolutePath

            // Correct EXIF rotation before cropping
            val exif = ExifInterface(file.absolutePath)
            val rotation = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            val bitmap = if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            } else raw

            val imgW = bitmap.width.toFloat()
            val imgH = bitmap.height.toFloat()

            // Screen dimensions used by the overlay
            val screenW = binding.cropOverlay.width.toFloat()
            val screenH = binding.cropOverlay.height.toFloat()

            // Replicate CropOverlayView's rect calculation
            val guideSize = minOf(screenW, screenH) * 0.82f
            val guideCx = screenW / 2f
            val guideCy = screenH / 2f - 40f

            // Normalised coordinates (0..1) relative to screen
            val normLeft   = (guideCx - guideSize / 2f) / screenW
            val normTop    = (guideCy - guideSize / 2f) / screenH
            val normRight  = (guideCx + guideSize / 2f) / screenW
            val normBottom = (guideCy + guideSize / 2f) / screenH

            // Map to image pixels — camera image may be wider or taller than screen
            // PreviewView default (FILL_CENTER) scales so the shorter axis fills the screen
            val previewScale = maxOf(screenW / imgW, screenH / imgH)
            val scaledW = imgW * previewScale
            val scaledH = imgH * previewScale
            val offsetX = (scaledW - screenW) / 2f  // pixels of scaled image cropped on each side
            val offsetY = (scaledH - screenH) / 2f

            val left   = ((normLeft   * screenW + offsetX) / previewScale).toInt().coerceIn(0, bitmap.width)
            val top    = ((normTop    * screenH + offsetY) / previewScale).toInt().coerceIn(0, bitmap.height)
            val right  = ((normRight  * screenW + offsetX) / previewScale).toInt().coerceIn(0, bitmap.width)
            val bottom = ((normBottom * screenH + offsetY) / previewScale).toInt().coerceIn(0, bitmap.height)

            val cropW = (right - left).coerceAtLeast(1)
            val cropH = (bottom - top).coerceAtLeast(1)

            val cropped = Bitmap.createBitmap(bitmap, left, top, cropW, cropH)
            val outFile = File(cacheDir, "crop_${System.currentTimeMillis()}.jpg")
            outFile.outputStream().use { cropped.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            Log.d("RecordPricer", "Cropped: ${left},${top} ${cropW}x${cropH} from ${bitmap.width}x${bitmap.height}")
            outFile.absolutePath
        } catch (e: Exception) {
            Log.e("RecordPricer", "Crop failed", e)
            file.absolutePath  // fall back to full image
        }
    }

    companion object {
        const val EXTRA_BARCODE = "barcode"
        const val EXTRA_PHOTO_PATH = "photo_path"
    }
}
