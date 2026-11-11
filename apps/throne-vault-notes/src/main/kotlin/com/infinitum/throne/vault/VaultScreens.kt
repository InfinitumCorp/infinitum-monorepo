package com.infinitum.throne.vault

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.QRCodeWriter
import com.infinitum.throne.core.crypto.VaultMessages
import com.infinitum.throne.ui.ThroneButton
import com.infinitum.throne.ui.ThroneCard
import com.infinitum.throne.ui.ThroneInk
import com.infinitum.throne.ui.ThroneMuted
import com.infinitum.throne.ui.Wordmark
import com.infinitum.throne.ui.throneFieldColors

@Composable
fun VaultApp(viewModel: VaultViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (viewModel.screen) {
            VaultScreen.Home -> Home(viewModel)
            VaultScreen.Compose -> Compose(viewModel)
            VaultScreen.Open -> Open(viewModel)
            VaultScreen.Identity -> Identity(viewModel)
            VaultScreen.Notes -> Notes(viewModel)
        }
    }
}

@Composable
private fun Home(viewModel: VaultViewModel) {
    Wordmark(edition = viewModel.edition.label, kicker = "Vault")
    Text(
        "This does not change Google Messages. It encrypts text on this phone. Messages only receives ciphertext. Plain SMS and MMS stay unencrypted if you type them in Messages.",
        style = MaterialTheme.typography.bodyMedium,
    )
    viewModel.banner?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    ThroneButton(text = "Secure Compose", onClick = { viewModel.show(VaultScreen.Compose) })
    ThroneButton(text = "Open a message", onClick = { viewModel.show(VaultScreen.Open) })
    ThroneButton(text = "Identity", onClick = { viewModel.show(VaultScreen.Identity) })
    ThroneButton(text = "Notes on this device", onClick = { viewModel.show(VaultScreen.Notes) })
}

@Composable
private fun Compose(viewModel: VaultViewModel) {
    val context = LocalContext.current
    Text("SECURE COMPOSE", style = MaterialTheme.typography.titleMedium)
    Text(
        "Passphrase mode needs the same passphrase on both phones, agreed in person. Contact mode encrypts to an X25519 key. Compare the safety number in person before you trust a key that arrived by SMS.",
        style = MaterialTheme.typography.bodyMedium,
        color = ThroneMuted,
    )
    OutlinedTextField(
        value = viewModel.composeBody,
        onValueChange = { viewModel.composeBody = it.take(VaultMessages.MAX_PLAINTEXT_BYTES) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Message") },
        minLines = 4,
        colors = throneFieldColors(),
    )
    ThroneButton(
        text = "Passphrase",
        onClick = { viewModel.mode = ComposeMode.Passphrase },
    )
    ThroneButton(
        text = "Contact key",
        onClick = { viewModel.mode = ComposeMode.Contact },
    )
    Text(
        if (viewModel.mode == ComposeMode.Passphrase) "Mode: passphrase, ${VaultMessages.PBKDF2_ITERATIONS} PBKDF2-HMAC-SHA256 iterations, AES-256-GCM."
        else "Mode: X25519, ephemeral key, HKDF-SHA256, AES-256-GCM.",
        style = MaterialTheme.typography.bodySmall,
        color = ThroneMuted,
    )
    if (viewModel.mode == ComposeMode.Passphrase) {
        PassphraseFields(
            first = viewModel.passphrase,
            second = viewModel.passphraseAgain,
            onFirst = { viewModel.passphrase = it },
            onSecond = { viewModel.passphraseAgain = it },
            repeat = true,
        )
    } else {
        viewModel.contacts.forEach { contact ->
            ThroneButton(
                text = if (viewModel.selectedContact == contact.name) "Using ${contact.name}" else contact.name,
                onClick = { viewModel.selectedContact = contact.name },
            )
        }
        OutlinedTextField(
            value = viewModel.pastedKey,
            onValueChange = { viewModel.pastedKey = it.take(400) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Or paste a public key") },
            colors = throneFieldColors(),
        )
    }
    ThroneButton(
        text = if (viewModel.keepCopy) "Keep ciphertext on this device" else "Do not keep a copy",
        onClick = { viewModel.keepCopy = !viewModel.keepCopy },
    )
    ThroneButton(
        text = if (viewModel.busy) "Working" else "Encrypt",
        onClick = viewModel::encrypt,
        enabled = !viewModel.busy,
    )
    viewModel.ciphertext?.let { armored ->
        Text("Ciphertext. This is what Messages will carry.", style = MaterialTheme.typography.bodySmall, color = ThroneMuted)
        SelectionContainer {
            Text(VaultMessages.shareText(armored), style = MaterialTheme.typography.bodySmall)
        }
        ThroneButton(text = "Share to Messages", onClick = { viewModel.shareToMessages(context) })
        ThroneButton(text = "Copy ciphertext", onClick = { viewModel.copyCiphertext(context) })
    }
    viewModel.banner?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    ThroneButton(text = "Back", onClick = { viewModel.show(VaultScreen.Home) })
}

@Composable
private fun Open(viewModel: VaultViewModel) {
    Text("OPEN A MESSAGE", style = MaterialTheme.typography.titleMedium)
    Text(
        "Paste the THRONEVAULT1 text, or share it into this app from Messages. A passphrase message asks for the passphrase. A contact message opens with the key on this phone.",
        style = MaterialTheme.typography.bodyMedium,
        color = ThroneMuted,
    )
    OutlinedTextField(
        value = viewModel.openInput,
        onValueChange = { viewModel.openInput = it.take(VaultMessages.MAX_INPUT_CHARS) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Ciphertext") },
        minLines = 4,
        colors = throneFieldColors(),
    )
    OutlinedTextField(
        value = viewModel.openPassphrase,
        onValueChange = { viewModel.openPassphrase = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Passphrase, if this message uses one") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        colors = throneFieldColors(),
    )
    ThroneButton(
        text = if (viewModel.busy) "Working" else "Open",
        onClick = viewModel::openMessage,
        enabled = !viewModel.busy,
    )
    viewModel.plaintext?.let { text ->
        ThroneCard {
            Text("Plaintext stays on this screen. It is not written to disk.", style = MaterialTheme.typography.bodySmall, color = ThroneMuted)
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        }
        ThroneButton(text = "Clear", onClick = viewModel::clearPlaintext)
    }
    viewModel.banner?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    ThroneButton(text = "Back", onClick = { viewModel.show(VaultScreen.Home) })
}

@Composable
private fun Identity(viewModel: VaultViewModel) {
    val context = LocalContext.current
    Text("IDENTITY", style = MaterialTheme.typography.titleMedium)
    if (!viewModel.identityReady || viewModel.publicPayload == null) {
        Text(
            viewModel.banner ?: "The identity key is not ready. Passphrase messages still work.",
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        Text(
            "This is the public key. The private key stays in Android Keystore, or wrapped by a Keystore key. Showing this QR does not authenticate you. Read the safety number to the other person in person.",
            style = MaterialTheme.typography.bodyMedium,
        )
        QrImage(viewModel.publicPayload!!)
        Text(viewModel.safetyNumber.orEmpty(), style = MaterialTheme.typography.bodyLarge)
        SelectionContainer {
            Text(viewModel.publicPayload.orEmpty(), style = MaterialTheme.typography.bodySmall)
        }
        ThroneButton(text = "Copy public key", onClick = { viewModel.copyPublicKey(context) })
        Text("Save a contact", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        OutlinedTextField(
            value = viewModel.contactName,
            onValueChange = { viewModel.contactName = it.take(80) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Name") },
            singleLine = true,
            colors = throneFieldColors(),
        )
        OutlinedTextField(
            value = viewModel.contactKey,
            onValueChange = { viewModel.contactKey = it.take(400) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Their public key") },
            colors = throneFieldColors(),
        )
        ThroneButton(text = "Save contact", onClick = viewModel::saveContact)
        viewModel.contacts.forEach { contact ->
            ThroneCard {
                Text(contact.name, style = MaterialTheme.typography.bodyLarge)
                Text(VaultMessages.safetyNumber(contact.rawPublic), style = MaterialTheme.typography.bodySmall, color = ThroneMuted)
                ThroneButton(
                    text = "Remove",
                    onClick = { viewModel.removeContact(contact.name) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
    viewModel.banner?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    ThroneButton(text = "Back", onClick = { viewModel.show(VaultScreen.Home) })
}

@Composable
private fun Notes(viewModel: VaultViewModel) {
    Text("NOTES ON THIS DEVICE", style = MaterialTheme.typography.titleMedium)
    Text(
        "These notes are sealed with the Android Keystore key on this phone. They are not sent to Messages. A factory reset or a broken keystore destroys them. There is no cloud copy.",
        style = MaterialTheme.typography.bodyMedium,
        color = ThroneMuted,
    )
    OutlinedTextField(
        value = viewModel.noteDraft,
        onValueChange = { viewModel.noteDraft = it.take(VaultMessages.MAX_PLAINTEXT_BYTES) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Note") },
        minLines = 3,
        colors = throneFieldColors(),
    )
    ThroneButton(text = "Seal on this device", onClick = viewModel::saveNote)
    viewModel.notes.forEach { note ->
        ThroneCard {
            Text(note.text, style = MaterialTheme.typography.bodyMedium)
            ThroneButton(
                text = "Delete",
                onClick = { viewModel.deleteNote(note.id) },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    viewModel.banner?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    ThroneButton(text = "Back", onClick = { viewModel.show(VaultScreen.Home) })
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun PassphraseFields(
    first: String,
    second: String,
    onFirst: (String) -> Unit,
    onSecond: (String) -> Unit,
    repeat: Boolean,
) {
    OutlinedTextField(
        value = first,
        onValueChange = onFirst,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Passphrase") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        colors = throneFieldColors(),
    )
    if (repeat) {
        OutlinedTextField(
            value = second,
            onValueChange = onSecond,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Repeat passphrase") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            colors = throneFieldColors(),
        )
    }
}

@Composable
private fun QrImage(payload: String) {
    val matrix = remember(payload) {
        QRCodeWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            512,
            512,
            mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            ),
        )
    }
    Canvas(Modifier.size(220.dp)) {
        val cell = size.width / matrix.width
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                if (matrix.get(x, y)) {
                    drawRect(
                        color = ThroneInk,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell + 0.5f, cell + 0.5f),
                    )
                }
            }
        }
    }
}
