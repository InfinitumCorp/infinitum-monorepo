package com.infinitum.eidetic.cam

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
import androidx.compose.foundation.layout.height
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
import com.infinitum.throne.core.eidetic.ConsentStatement
import com.infinitum.throne.ui.ThroneButton
import com.infinitum.throne.ui.ThroneCard
import com.infinitum.throne.ui.ThroneMuted
import com.infinitum.throne.ui.Wordmark
import com.infinitum.throne.ui.throneFieldColors
import java.time.Instant
import java.util.concurrent.Executors

@Composable
fun EideticApp(model: EideticViewModel) {
    if (model.fatal != null) {
        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Wordmark(edition = model.editionTitle, kicker = "Eidetic")
            Text(model.fatal ?: "")
        }
        return
    }
    if (model.screen == EideticScreen.Camera) {
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
            EideticScreen.Home -> Home(model)
            EideticScreen.Enroll -> Enroll(model)
            EideticScreen.Subjects -> Subjects(model)
            EideticScreen.Capture -> Capture(model)
            EideticScreen.Queue -> Queue(model)
            EideticScreen.Desk -> Desk(model)
            EideticScreen.Delete -> Delete(model)
            EideticScreen.Camera -> Unit
        }
    }
}

@Composable
private fun Home(model: EideticViewModel) {
    Wordmark(edition = model.editionTitle, kicker = "Eidetic")
    Text(
        "Frames are captured only for a person whose written consent is on file. The desk's policy check runs before anything is queued. A person reviews the result. This app takes no action from a match.",
        style = MaterialTheme.typography.bodyMedium,
    )
    model.banner?.let { Text(it) }
    if (model.unreadable > 0) {
        Text(
            "Some enrollment files could not be read. Delete my data removes them.",
            color = ThroneMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    ThroneButton(text = "Enroll", onClick = { model.show(EideticScreen.Enroll) })
    ThroneButton(text = "Enrolled people", onClick = { model.show(EideticScreen.Subjects) })
    ThroneButton(text = "Reviewer queue", onClick = { model.show(EideticScreen.Queue) })
    ThroneButton(text = "Desk", onClick = { model.show(EideticScreen.Desk) })
    ThroneButton(text = "Delete my data", onClick = { model.show(EideticScreen.Delete) })
}

@Composable
private fun Enroll(model: EideticViewModel) {
    Text("ENROLL", style = MaterialTheme.typography.titleMedium)
    model.banner?.let { Text(it) }
    ThroneCard {
        Column(Modifier.height(240.dp).verticalScroll(rememberScrollState())) {
            Text(ConsentStatement.TEXT, style = MaterialTheme.typography.bodySmall)
        }
    }
    Text(
        "${ConsentStatement.VERSION}  ${ConsentStatement.sha256().take(16)}",
        color = ThroneMuted,
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedTextField(
        value = model.name,
        onValueChange = { model.name = it.take(120) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Legal name") },
        colors = throneFieldColors(),
    )
    OutlinedTextField(
        value = model.purposesText,
        onValueChange = { model.purposesText = it.take(700) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Purposes, one per line") },
        minLines = 3,
        colors = throneFieldColors(),
    )
    OutlinedTextField(
        value = model.retentionText,
        onValueChange = { model.retentionText = it.filter(Char::isDigit).take(2) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Retention days, 1 to 30") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = throneFieldColors(),
    )
    CheckRow(
        checked = model.consentOnFile,
        text = "Written consent for this person is on file.",
        onChecked = { model.consentOnFile = it },
    )
    OutlinedTextField(
        value = model.acknowledgment,
        onValueChange = { model.acknowledgment = it.take(80) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(ConsentStatement.ACKNOWLEDGMENT) },
        colors = throneFieldColors(),
    )
    ThroneButton(text = "Record enrollment", onClick = { model.enroll() }, enabled = !model.busy)
    ThroneButton(text = "Back", onClick = { model.show(EideticScreen.Home) })
}

@Composable
private fun Subjects(model: EideticViewModel) {
    Text("ENROLLED", style = MaterialTheme.typography.titleMedium)
    model.banner?.let { Text(it) }
    if (model.subjects.isEmpty()) {
        Text("No one is enrolled on this phone.", color = ThroneMuted)
    }
    model.subjects.forEach { subject ->
        ThroneButton(
            text = "${subject.legalName}  ${subject.id.take(8)}",
            onClick = { model.select(subject.id) },
        )
    }
    ThroneButton(text = "Back", onClick = { model.show(EideticScreen.Home) })
}

@Composable
private fun Capture(model: EideticViewModel) {
    val context = LocalContext.current
    val subject = model.selected
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.openCamera() else model.permissionDenied()
    }
    Text("CAPTURE", style = MaterialTheme.typography.titleMedium)
    model.banner?.let { Text(it) }
    if (subject == null) {
        Text("Choose an enrolled person.", color = ThroneMuted)
    } else {
        Text(subject.legalName, style = MaterialTheme.typography.bodyLarge)
        Text(
            "Point the camera only at this person. This app does not identify anyone else.",
            color = ThroneMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        subject.consent.purposes.forEach { purpose ->
            val selected = model.selectedPurpose == purpose
            ThroneButton(
                text = if (selected) "Purpose  $purpose" else purpose,
                onClick = { model.selectedPurpose = purpose },
            )
        }
        ThroneButton(
            text = "Open camera",
            enabled = !model.busy,
            onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (granted) model.openCamera() else permission.launch(Manifest.permission.CAMERA)
            },
        )
    }
    ThroneButton(text = "App settings", onClick = { openSettings(context) })
    ThroneButton(text = "Back", onClick = { model.show(EideticScreen.Subjects) })
}

@Composable
private fun CameraScreen(model: EideticViewModel) {
    val context = LocalContext.current
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    val subject = model.selected
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
        Text(subject?.legalName ?: "No enrolled person", color = ThroneMuted)
        Text(model.selectedPurpose, style = MaterialTheme.typography.bodySmall)
        CheckRow(
            checked = model.personInFrame,
            text = "The person in frame is this enrolled subject.",
            onChecked = { model.personInFrame = it },
        )
        model.banner?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        ThroneButton(
            text = "Take frame",
            enabled = model.cameraBound && model.personInFrame && !model.busy,
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
                                if (bytes == null) model.onCaptureFailed() else model.onJpeg(bytes, true)
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            ContextCompat.getMainExecutor(context).execute { model.onCaptureFailed() }
                        }
                    },
                )
            },
        )
        ThroneButton(text = "Close camera", onClick = { model.show(EideticScreen.Capture) })
    }
}

@Composable
private fun Queue(model: EideticViewModel) {
    Text("REVIEWER QUEUE", style = MaterialTheme.typography.titleMedium)
    Text(
        "A person reviews these. This app does not unlock, alert, or decide anything from a match.",
        color = ThroneMuted,
        style = MaterialTheme.typography.bodySmall,
    )
    model.banner?.let { Text(it) }
    if (model.receipts.isEmpty()) Text("Nothing is queued from this phone.", color = ThroneMuted)
    model.receipts.forEach { receipt ->
        ThroneCard {
            Text(receipt.queueId, style = MaterialTheme.typography.bodyMedium)
            Text(receipt.purpose, color = ThroneMuted, style = MaterialTheme.typography.bodySmall)
            Text("Status ${receipt.status}", style = MaterialTheme.typography.bodySmall)
            Text(Instant.ofEpochMilli(receipt.submittedAtEpochMs).toString(), color = ThroneMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
    ThroneButton(text = "Refresh", onClick = { model.refreshQueue() }, enabled = !model.busy)
    ThroneButton(text = "Back", onClick = { model.show(EideticScreen.Home) })
}

@Composable
private fun Desk(model: EideticViewModel) {
    Text("DESK", style = MaterialTheme.typography.titleMedium)
    Text(
        "Loopback only. Use adb reverse so this port reaches the eidetic API. A frame is sent only after the policy check allows it.",
        color = ThroneMuted,
        style = MaterialTheme.typography.bodySmall,
    )
    model.banner?.let { Text(it) }
    OutlinedTextField(
        value = model.deskField,
        onValueChange = { model.deskField = it.take(200) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("http://127.0.0.1:port") },
        colors = throneFieldColors(),
    )
    ThroneButton(text = "Save desk", onClick = { model.saveDesk() })
    ThroneButton(text = "Back", onClick = { model.show(EideticScreen.Home) })
}

@Composable
private fun Delete(model: EideticViewModel) {
    Text("DELETE MY DATA", style = MaterialTheme.typography.titleMedium)
    Text(
        "This erases enrollments and queue receipts on this phone, and asks the desk to delete each enrolled person. Remote deletion is confirmed only when the desk says so.",
        color = ThroneMuted,
        style = MaterialTheme.typography.bodySmall,
    )
    model.banner?.let { Text(it) }
    OutlinedTextField(
        value = model.deleteConfirm,
        onValueChange = { model.deleteConfirm = it.take(120) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Type a name, or DELETE") },
        colors = throneFieldColors(),
    )
    model.subjects.forEach { subject ->
        ThroneButton(
            text = "Delete ${subject.legalName}",
            enabled = !model.busy,
            onClick = { model.deleteSubject(subject) },
        )
    }
    ThroneButton(text = "Delete everything on this phone", enabled = !model.busy, onClick = { model.deleteAll() })
    ThroneButton(text = "Back", onClick = { model.show(EideticScreen.Home) })
}

@Composable
private fun CheckRow(checked: Boolean, text: String, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(text, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun openSettings(context: android.content.Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )
    context.startActivity(intent)
}

private fun jpegBytes(image: ImageProxy): ByteArray? {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    if (buffer.remaining() <= 0) return null
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return bytes
}
