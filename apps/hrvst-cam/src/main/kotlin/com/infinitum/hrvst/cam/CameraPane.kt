package com.infinitum.hrvst.cam

import android.util.Size
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.infinitum.throne.ui.ThroneInk
import com.infinitum.throne.ui.ThroneLine
import com.infinitum.throne.ui.ThroneMuted

@Composable
internal fun CameraPane(
    bound: Boolean,
    onBound: (Boolean) -> Unit,
    onReady: (ImageCapture) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val owner = LocalLifecycleOwner.current
    val onBoundState = rememberUpdatedState(onBound)
    val onReadyState = rememberUpdatedState(onReady)
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            setBackgroundColor(android.graphics.Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
    }
    val imageCapture = remember {
        val strategy = ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setJpegQuality(80)
            .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(strategy).build())
            .build()
    }

    DisposableEffect(owner) {
        val executor = ContextCompat.getMainExecutor(context)
        var provider: ProcessCameraProvider? = null
        fun unbind() {
            onBoundState.value(false)
            try {
                provider?.unbindAll()
            } catch (_: Exception) {
            }
        }
        fun bind() {
            val cameraProvider = provider ?: return
            try {
                val preview = Preview.Builder().build()
                preview.surfaceProvider = previewView.surfaceProvider
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                onReadyState.value(imageCapture)
                onBoundState.value(true)
            } catch (_: Exception) {
                onBoundState.value(false)
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> bind()
                Lifecycle.Event.ON_STOP -> unbind()
                else -> Unit
            }
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                provider = future.get()
                if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) bind()
            } catch (_: Exception) {
                onBoundState.value(false)
            }
        }, executor)
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            unbind()
        }
    }

    Column(modifier) {
        Text(
            text = if (bound) "CAMERA ACTIVE" else "CAMERA OFF",
            style = MaterialTheme.typography.titleMedium,
            color = if (bound) ThroneInk else ThroneMuted,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, if (bound) ThroneInk else ThroneLine)
                .padding(horizontal = 12.dp, vertical = 14.dp),
        )
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}
