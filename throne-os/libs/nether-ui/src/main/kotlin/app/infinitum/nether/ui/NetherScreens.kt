package app.infinitum.nether.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import app.infinitum.nether.core.EntryEdits
import app.infinitum.nether.core.Health
import app.infinitum.nether.core.Importers
import app.infinitum.nether.core.NetherEntry
import app.infinitum.nether.core.NetherKdf
import app.infinitum.nether.core.NetherVault
import app.infinitum.nether.core.PasswordGenerator
import app.infinitum.nether.core.Totp
import app.infinitum.nether.core.VaultDocument
import com.infinitum.throne.ui.ThroneButton
import com.infinitum.throne.ui.ThroneInk
import com.infinitum.throne.ui.ThroneMuted
import com.infinitum.throne.ui.ThronePaper
import com.infinitum.throne.ui.throneFieldColors
import org.json.JSONObject
import java.util.Base64

private enum class Screen { LOCK, CREATE, LIST, EDIT, GENERATE, TOTP, HEALTH, SETTINGS }

@Composable
fun NetherRoot(store: NetherStore) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val generation = NetherSession.generation
    var screen by remember(generation) {
        mutableStateOf(
            when {
                NetherSession.unlocked -> Screen.LIST
                store.read(VaultFiles.PRIMARY) == null -> Screen.CREATE
                else -> Screen.LOCK
            },
        )
    }
    var message by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(NetherEntry()) }
    var favoritesOnly by remember { mutableStateOf(false) }
    val meta = remember { Meta(store) }
    val scan = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringExtra(QrScanActivity.EXTRA).orEmpty()
        if (result.resultCode == Activity.RESULT_OK && text.startsWith("otpauth://")) {
            editing = editing.copy(totp = text)
            screen = Screen.EDIT
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ThronePaper)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 22.dp),
    ) {
        Text("Nether", style = MaterialTheme.typography.headlineSmall, color = ThroneInk, modifier = Modifier.padding(top = 12.dp))
        if (message.isNotEmpty()) {
            Text(message, color = ThroneMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(12.dp))
        when (screen) {
            Screen.CREATE -> CreateVault(onCreate = { password, duress ->
                val blob = NetherVault.create(password)
                store.write(VaultFiles.PRIMARY, blob)
                if (duress != null) store.write(VaultFiles.DECOY, NetherVault.create(duress))
                val unlock = NetherVault.unlock(password, blob)
                NetherSession.unlock(unlock.document, unlock.key, decoy = false)
                meta.write(meta.read().put("failures", 0))
                screen = Screen.LIST
                message = ""
            })
            Screen.LOCK -> LockScreen(
                biometric = meta.biometric() != null && BiometricVault.available(activity),
                onUnlock = { password ->
                    val failures = meta.failures()
                    if (meta.wipeAfter() > 0 && failures >= meta.wipeAfter()) {
                        store.delete(VaultFiles.PRIMARY)
                        store.delete(VaultFiles.DECOY)
                        message = "Vault wiped."
                        screen = Screen.CREATE
                        return@LockScreen
                    }
                    message = "Checking."
                    Thread {
                        Thread.sleep(NetherKdf.delayMillis(failures))
                        val primary = store.read(VaultFiles.PRIMARY)
                        val decoy = store.read(VaultFiles.DECOY)
                        val opened = primary?.let { runCatching { NetherVault.unlock(password, it) }.getOrNull() }
                        val decoyOpen = if (opened == null) decoy?.let { runCatching { NetherVault.unlock(password, it) }.getOrNull() } else null
                        activity.runOnUiThread {
                            when {
                                opened != null -> {
                                    NetherSession.unlock(opened.document, opened.key, decoy = false)
                                    meta.write(meta.read().put("failures", 0))
                                    screen = Screen.LIST
                                    message = ""
                                }
                                decoyOpen != null -> {
                                    NetherSession.unlock(decoyOpen.document, decoyOpen.key, decoy = true)
                                    screen = Screen.LIST
                                    message = ""
                                }
                                else -> {
                                    val next = failures + 1
                                    meta.write(meta.read().put("failures", next))
                                    message = "Wrong password."
                                }
                            }
                        }
                    }.start()
                },
                onBiometric = {
                    val packed = meta.biometric() ?: return@LockScreen
                    BiometricVault.unlock(activity, packed, onKey = { key ->
                        val blob = store.read(VaultFiles.PRIMARY) ?: return@unlock
                        val doc = VaultDocumentFrom(key, blob)
                        NetherSession.unlock(doc, key, decoy = false)
                        screen = Screen.LIST
                    }, onError = { message = it })
                },
            )
            Screen.LIST -> EntryList(
                query = query,
                onQuery = { query = it },
                favoritesOnly = favoritesOnly,
                onToggleFavorites = { favoritesOnly = !favoritesOnly },
                onOpen = { editing = it; screen = Screen.EDIT },
                onAdd = { editing = NetherEntry(); screen = Screen.EDIT },
                onGenerate = { screen = Screen.GENERATE },
                onTotp = { screen = Screen.TOTP },
                onHealth = { screen = Screen.HEALTH },
                onSettings = { screen = Screen.SETTINGS },
            )
            Screen.EDIT -> Editor(
                entry = editing,
                onChange = { editing = it },
                onSave = {
                    saveEntry(store, editing)
                    screen = Screen.LIST
                },
                onScan = {
                    scan.launch(Intent(context, QrScanActivity::class.java))
                },
                onBack = { screen = Screen.LIST },
            )
            Screen.GENERATE -> GeneratorScreen(onUse = {
                editing = editing.copy(secret = it)
                screen = Screen.EDIT
            }, onBack = { screen = Screen.LIST })
            Screen.TOTP -> TotpScreen(onBack = { screen = Screen.LIST }, onCopy = { NetherClipboard.copy(context, it) })
            Screen.HEALTH -> HealthScreen(onBack = { screen = Screen.LIST })
            Screen.SETTINGS -> SettingsScreen(
                store = store,
                meta = meta,
                activity = activity,
                onBack = { screen = Screen.LIST },
                onLock = {
                    NetherSession.lock()
                    screen = Screen.LOCK
                },
            )
        }
    }
}

private fun VaultDocumentFrom(key: ByteArray, blob: ByteArray): app.infinitum.nether.core.VaultDocument {
    return app.infinitum.nether.core.VaultDocument.fromJson(app.infinitum.nether.core.NetherCipher.open(key, blob))
}

private fun saveEntry(store: NetherStore, entry: NetherEntry) {
    val key = NetherSession.key ?: return
    val current = NetherSession.document ?: return
    val previous = current.entries.firstOrNull { it.id == entry.id }
    val next = if (previous != null && previous.secret != entry.secret) {
        EntryEdits.withNewSecret(previous, entry.secret)
            .copy(
                title = entry.title,
                username = entry.username,
                notes = entry.notes,
                url = entry.url,
                folder = entry.folder,
                tags = entry.tags,
                favorite = entry.favorite,
                totp = entry.totp,
                type = entry.type,
                attachments = entry.attachments,
            )
    } else {
        entry
    }
    val entries = current.entries.filterNot { it.id == next.id } + next
    val document = current.copy(entries = entries.sortedBy { it.title.lowercase() })
    val blob = store.read(if (NetherSession.decoy) VaultFiles.DECOY else VaultFiles.PRIMARY) ?: return
    val sealed = NetherVault.save(key, blob, document)
    store.write(if (NetherSession.decoy) VaultFiles.DECOY else VaultFiles.PRIMARY, sealed)
    NetherSession.document = document
}

@Composable
private fun CreateVault(onCreate: (CharArray, CharArray?) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var duress by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Create the vault. It stays on this phone.", color = ThroneMuted)
        SecretField("Master password", password) { password = it }
        SecretField("Confirm", confirm) { confirm = it }
        SecretField("Duress PIN (optional)", duress) { duress = it }
        ThroneButton("Create", {
            if (password.length >= 8 && password == confirm) {
                onCreate(password.toCharArray(), duress.toCharArray().takeIf { duress.length >= 4 })
            }
        })
    }
}

@Composable
private fun LockScreen(biometric: Boolean, onUnlock: (CharArray) -> Unit, onBiometric: () -> Unit) {
    var password by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SecretField("Master password", password) { password = it }
        ThroneButton("Unlock", { onUnlock(password.toCharArray()) })
        if (biometric) ThroneButton("Biometric", onBiometric)
    }
}

@Composable
private fun EntryList(
    query: String,
    onQuery: (String) -> Unit,
    favoritesOnly: Boolean,
    onToggleFavorites: () -> Unit,
    onOpen: (NetherEntry) -> Unit,
    onAdd: () -> Unit,
    onGenerate: () -> Unit,
    onTotp: () -> Unit,
    onHealth: () -> Unit,
    onSettings: () -> Unit,
) {
    val doc = NetherSession.document ?: return
    val shown = doc.entries.filter {
        (!favoritesOnly || it.favorite) &&
            (query.isBlank() || it.title.contains(query, true) || it.username.contains(query, true) || it.folder.contains(query, true))
    }
    Column {
        OutlinedTextField(query, onQuery, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search") }, colors = throneFieldColors(), singleLine = true)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThroneButton("New", onAdd)
            ThroneButton(if (favoritesOnly) "All" else "Favorites", onToggleFavorites)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThroneButton("Generate", onGenerate)
            ThroneButton("Codes", onTotp)
            ThroneButton("Health", onHealth)
            ThroneButton("Settings", onSettings)
        }
        LazyColumn(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { entry ->
                Text(
                    "${entry.title.ifBlank { "Untitled" }}  ${entry.type}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(entry) }
                        .padding(vertical = 8.dp),
                    color = ThroneInk,
                )
            }
        }
    }
}

@Composable
private fun Editor(entry: NetherEntry, onChange: (NetherEntry) -> Unit, onSave: () -> Unit, onScan: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Field("Title", entry.title) { onChange(entry.copy(title = it)) }
        Field("Type ${entry.type}", entry.type) { onChange(entry.copy(type = it)) }
        Field("Username", entry.username) { onChange(entry.copy(username = it)) }
        SecretField("Secret", entry.secret) { onChange(entry.copy(secret = it)) }
        Field("URL", entry.url) { onChange(entry.copy(url = it)) }
        Field("Folder", entry.folder) { onChange(entry.copy(folder = it)) }
        Field("Tags", entry.tags.joinToString(",")) { onChange(entry.copy(tags = it.split(',').map { tag -> tag.trim() }.filter { tag -> tag.isNotEmpty() })) }
        Field("Notes", entry.notes) { onChange(entry.copy(notes = it)) }
        Field("otpauth", entry.totp) { onChange(entry.copy(totp = it)) }
        Text("Strength ${PasswordGenerator.strength(entry.secret)} / 4", color = ThroneMuted)
        if (entry.history.isNotEmpty()) Text("History ${entry.history.size}", color = ThroneMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThroneButton("Save", onSave)
            ThroneButton("Scan", onScan)
            ThroneButton("Copy", { NetherClipboard.copy(context, entry.secret) })
            ThroneButton("Back", onBack)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun GeneratorScreen(onUse: (String) -> Unit, onBack: () -> Unit) {
    var length by remember { mutableStateOf("20") }
    var symbols by remember { mutableStateOf(true) }
    var made by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Field("Length", length) { length = it.filter { ch -> ch.isDigit() }.take(3) }
        ThroneButton(if (symbols) "Symbols on" else "Symbols off", { symbols = !symbols })
        ThroneButton("Password", { made = PasswordGenerator.password(length.toIntOrNull() ?: 20, symbols = symbols) })
        ThroneButton("Passphrase", { made = PasswordGenerator.passphrase() })
        if (made.isNotEmpty()) {
            Text(made, color = ThroneInk)
            Text("Strength ${PasswordGenerator.strength(made)} / 4", color = ThroneMuted)
            ThroneButton("Use", { onUse(made) })
        }
        ThroneButton("Back", onBack)
    }
}

@Composable
private fun TotpScreen(onBack: () -> Unit, onCopy: (String) -> Unit) {
    val doc = NetherSession.document
    val now = System.currentTimeMillis()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        doc?.entries?.filter { it.totp.startsWith("otpauth://") }?.forEach { entry ->
            val spec = runCatching { Totp.parseOtpAuth(entry.totp) }.getOrNull()
            val code = spec?.let { Totp.code(it.secret, now, it.period, it.digits, it.algorithm) } ?: "------"
            Text("${entry.title}  $code  ${Totp.remainingSeconds(now)}s", color = ThroneInk, modifier = Modifier.clickable { onCopy(code) })
        }
        ThroneButton("Back", onBack)
    }
}

@Composable
private fun HealthScreen(onBack: () -> Unit) {
    val report = NetherSession.document?.let { Health.report(it) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Weak: ${report?.weak?.joinToString().orEmpty()}", color = ThroneInk)
        Text("Reused: ${report?.reused?.joinToString().orEmpty()}", color = ThroneInk)
        Text("Old: ${report?.old?.joinToString().orEmpty()}", color = ThroneInk)
        Text("Checked on this phone. Nothing is sent.", color = ThroneMuted)
        ThroneButton("Back", onBack)
    }
}

@Composable
private fun SettingsScreen(
    store: NetherStore,
    meta: Meta,
    activity: FragmentActivity,
    onBack: () -> Unit,
    onLock: () -> Unit,
) {
    var wipe by remember { mutableStateOf(meta.wipeAfter().toString()) }
    var pasted by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Field("Wipe after N failures (0 off)", wipe) { wipe = it.filter { ch -> ch.isDigit() }.take(2) }
        ThroneButton("Save limit", {
            meta.write(meta.read().put("wipeAfter", wipe.toIntOrNull() ?: 0))
        })
        ThroneButton("Enroll biometric", {
            val key = NetherSession.key ?: return@ThroneButton
            BiometricVault.enroll(activity, key, onDone = { packed ->
                meta.write(meta.read().put("biometric", Base64.getEncoder().encodeToString(packed)))
            }, onError = {})
        })
        val export = store.read(if (NetherSession.decoy) VaultFiles.DECOY else VaultFiles.PRIMARY)
        if (export != null) {
            Text("Export is the encrypted .nether blob. Share it from a file manager in this app's files.", color = ThroneMuted)
        }
        Field("Import CSV or otpauth", pasted) { pasted = it }
        ThroneButton("Import", {
            val note = Importers.throneVaultNote(pasted)
            if (note != null) return@ThroneButton
            val key = NetherSession.key ?: return@ThroneButton
            val current = NetherSession.document ?: return@ThroneButton
            val imported = if (pasted.trimStart().startsWith("NTHR") || pasted.trimStart().startsWith("TkhU")) {
                emptyList()
            } else {
                Importers.csv(pasted)
            }
            if (imported.isNotEmpty()) {
                val document = current.copy(entries = current.entries + imported)
                val blob = store.read(if (NetherSession.decoy) VaultFiles.DECOY else VaultFiles.PRIMARY) ?: return@ThroneButton
                store.write(if (NetherSession.decoy) VaultFiles.DECOY else VaultFiles.PRIMARY, NetherVault.save(key, blob, document))
                NetherSession.document = document
            }
        })
        ThroneButton("Lock", onLock)
        ThroneButton("Back", onBack)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, colors = throneFieldColors(), singleLine = true)
}

@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value,
        onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions.Default,
        colors = throneFieldColors(),
        singleLine = true,
    )
}

private class Meta(private val store: NetherStore) {
    fun read(): JSONObject = store.read(VaultFiles.META)?.let { runCatching { JSONObject(it.toString(Charsets.UTF_8)) }.getOrNull() } ?: JSONObject()
    fun write(json: JSONObject) = store.write(VaultFiles.META, json.toString().toByteArray())
    fun failures(): Int = read().optInt("failures")
    fun wipeAfter(): Int = read().optInt("wipeAfter")
    fun biometric(): ByteArray? = read().optString("biometric").takeIf { it.isNotBlank() }?.let { Base64.getDecoder().decode(it) }
}
