package com.infinitum.hrvst.cam

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.infinitum.throne.core.hrvst.Hrvst
import com.infinitum.throne.ui.ThroneButton
import com.infinitum.throne.ui.ThroneCard
import com.infinitum.throne.ui.ThroneMuted
import com.infinitum.throne.ui.Wordmark
import com.infinitum.throne.ui.throneFieldColors
import java.time.Instant
import java.util.concurrent.Executors

@Composable
fun HrvstApp(model: HrvstViewModel) {
    if (model.fatal != null) {
        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Wordmark(edition = model.editionTitle, kicker = "Harvest")
            Text(model.fatal ?: "")
        }
        return
    }
    if (model.screen == HrvstScreen.Camera) {
        CameraScreen(model)
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (model.screen) {
            HrvstScreen.Home -> Home(model)
            HrvstScreen.Packet -> Packet(model)
            HrvstScreen.Camera -> Unit
        }
    }
}

@Composable
private fun Home(model: HrvstViewModel) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.openCamera() else model.permissionDenied()
    }
    Wordmark(edition = model.editionTitle, kicker = "Harvest")
    Text(
        "A capture becomes a Harvest Packet in the Personal Silo on this phone. Nothing is shared until you permit that one packet.",
        style = MaterialTheme.typography.bodyMedium,
    )
    model.banner?.let { Text(it) }
    if (model.unreadable > 0) {
        Text("Some packet files could not be read. Delete the silo removes them.", color = ThroneMuted, style = MaterialTheme.typography.bodySmall)
    }
    OutlinedTextField(
        value = model.ownerField,
        onValueChange = { model.ownerField = it.take(80) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Silo owner") },
        colors = throneFieldColors(),
    )
    OutlinedTextField(
        value = model.retentionField,
        onValueChange = { model.retentionField = it.filter(Char::isDigit).take(3) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Retention days, 1 to 90") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = throneFieldColors(),
    )
    ThroneButton(text = "Save silo", onClick = { model.saveOwner() })
    Text(
        model.meta?.let { "Silo ${it.id.take(8)}. Schema ${Hrvst.SILO_SCHEMA}." } ?: "The silo has no owner yet.",
        color = ThroneMuted,
        style = MaterialTheme.typography.bodySmall,
    )
    ThroneButton(
        text = "Open camera",
        enabled = !model.busy,
        onClick = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (granted) model.openCamera() else permission.launch(Manifest.permission.CAMERA)
        },
    )
    if (model.packets.isEmpty()) Text("No packets on this phone.", color = ThroneMuted)
    model.packets.forEach { packet ->
        ThroneButton(
            text = "${packet.contentSha256.take(12)}  ${Instant.ofEpochMilli(packet.capturedAtEpochMs)}",
            onClick = { model.open(packet.id) },
        )
    }
    OutlinedTextField(
        value = model.deleteConfirm,
        onValueChange = { model.deleteConfirm = it.take(20) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Type DELETE to erase the silo") },
        colors = throneFieldColors(),
    )
    ThroneButton(text = "Delete silo", enabled = !model.busy, onClick = { model.deleteAll() })
    ThroneButton(text = "App settings", onClick = { openSettings(context) })
}

@Composable
private fun Packet(model: HrvstViewModel) {
    val context = LocalContext.current
    val packet = model.openPacket
    Text("PACKET", style = MaterialTheme.typography.titleMedium)
    model.banner?.let { Text(it) }
    if (packet == null) {
        Text("This packet is gone.", color = ThroneMuted)
    } else {
        ThroneCard {
            Text(Hrvst.PACKET_SCHEMA, style = MaterialTheme.typography.bodySmall)
            Text(packet.id, style = MaterialTheme.typography.bodySmall)
            Text("sha256 ${packet.contentSha256}", style = MaterialTheme.typography.bodySmall)
            Text(
                "previous ${packet.provenance.previousContentSha256 ?: "none"}",
                color = ThroneMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Provenance links this frame to the previous hash on this phone. It is not a hardware root of trust.",
                color = ThroneMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            if (packet.grants.isEmpty()) {
                Text("No share permission.", color = ThroneMuted, style = MaterialTheme.typography.bodySmall)
            } else {
                packet.grants.forEach { grant ->
                    Text(
                        "${grant.recipient}  ${if (grant.includeImage) "image" else "manifest only"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        OutlinedTextField(
            value = model.recipientField,
            onValueChange = { model.recipientField = it.take(80) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Recipient for this packet") },
            colors = throneFieldColors(),
        )
        CheckRow(model.includeImage, "Include the image.") { model.includeImage = it }
        CheckRow(model.permitChecked, "I permit sharing this packet with the recipient named above.") { model.permitChecked = it }
        ThroneButton(
            text = "Share this packet",
            enabled = model.permitChecked && !model.busy,
            onClick = {
                val intent = model.prepareShare(context) ?: return@ThroneButton
                val chooser = Intent.createChooser(intent, "Share this packet")
                chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(chooser)
            },
        )
        ThroneButton(text = "Revoke permissions", onClick = { model.revoke() })
        OutlinedTextField(
            value = model.deleteConfirm,
            onValueChange = { model.deleteConfirm = it.take(20) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Type DELETE") },
            colors = throneFieldColors(),
        )
        ThroneButton(text = "Delete this packet", onClick = { model.deleteOpen() })
    }
    ThroneButton(text = "Back", onClick = { model.show(HrvstScreen.Home) })
}

@Composable
private fun CameraScreen(model: HrvstViewModel) {
    val context = LocalContext.current
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CameraPane(
            bound = model.cameraBound,
            onBound = { model.cameraBound = it },
            onReady = { capture = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Text("This frame stays in the Personal Silo until you share that packet.", color = ThroneMuted, style = MaterialTheme.typography.bodySmall)
        model.banner?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        ThroneButton(
            text = "Take frame",
            enabled = model.cameraBound && !model.busy,
            onClick = {
                val shot = capture ?: return@ThroneButton
                if (!model.armShutter()) return@ThroneButton
                shot.targetRotation = (context as Activity).windowManager.defaultDisplay.rotation
                shot.takePicture(
                    executor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bytes = jpegBytes(image)
                            image.close()
                            ContextCompat.getMainExecutor(context).execute {
                                if (bytes == null) model.onCaptureFailed() else model.onJpeg(bytes)
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            ContextCompat.getMainExecutor(context).execute { model.onCaptureFailed() }
                        }
                    },
                )
            },
        )
        ThroneButton(text = "Close camera", onClick = { model.show(HrvstScreen.Home) })
    }
}

@Composable
private fun CheckRow(checked: Boolean, text: String, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(text, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun openSettings(context: android.content.Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
}

private fun jpegBytes(image: ImageProxy): ByteArray? {
    val buffer = image.planes.firstOrNull()?.buffer ?: return null
    if (buffer.remaining() <= 0) return null
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return bytes
}
