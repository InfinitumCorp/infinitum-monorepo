package app.infinitum.nether.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class QrScanActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val preview = PreviewView(this)
        setContentView(preview)
        val found = AtomicBoolean(false)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            val previewUse = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
            val analysis = ImageAnalysis.Builder().build()
            analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { image ->
                val text = decode(image)
                image.close()
                if (text != null && text.startsWith("otpauth://") && found.compareAndSet(false, true)) {
                    runOnUiThread {
                        setResult(RESULT_OK, intent.putExtra(EXTRA, text))
                        finish()
                    }
                }
            }
            provider.unbindAll()
            provider.bindToLifecycle(this, androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA, previewUse, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun decode(image: ImageProxy): String? {
        return try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            val width = image.width
            val height = image.height
            val rowStride = plane.rowStride
            val tight = if (rowStride == width) {
                data
            } else {
                ByteArray(width * height).also { out ->
                    for (row in 0 until height) {
                        System.arraycopy(data, row * rowStride, out, row * width, width)
                    }
                }
            }
            val source = PlanarYUVLuminanceSource(tight, width, height, 0, 0, width, height, false)
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val EXTRA = "otpauth"
    }
}
