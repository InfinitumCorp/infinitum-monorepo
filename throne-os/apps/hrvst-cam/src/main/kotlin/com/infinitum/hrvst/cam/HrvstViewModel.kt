package com.infinitum.hrvst.cam

import android.app.Application
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infinitum.throne.core.cam.EditionTitle
import com.infinitum.throne.core.hrvst.HarvestPacket
import com.infinitum.throne.core.hrvst.HrvstRules
import com.infinitum.throne.core.hrvst.SharePermit
import com.infinitum.throne.core.hrvst.SiloMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class HrvstScreen { Home, Camera, Packet }

class HrvstViewModel(app: Application) : AndroidViewModel(app) {
    val editionTitle = EditionTitle.title(BuildConfig.EDITION)
    var screen by mutableStateOf(HrvstScreen.Home)
        private set
    var banner by mutableStateOf<String?>(null)
        private set
    var fatal by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var cameraBound by mutableStateOf(false)
    var meta by mutableStateOf<SiloMeta?>(null)
        private set
    var packets by mutableStateOf<List<HarvestPacket>>(emptyList())
        private set
    var unreadable by mutableStateOf(0)
        private set
    var ownerField by mutableStateOf("")
    var retentionField by mutableStateOf("30")
    var openId by mutableStateOf<String?>(null)
    var recipientField by mutableStateOf("")
    var includeImage by mutableStateOf(false)
    var permitChecked by mutableStateOf(false)
    var deleteConfirm by mutableStateOf("")

    private var store: HrvstStore? = null
    val openPacket: HarvestPacket? get() = packets.find { it.id == openId }

    init {
        try {
            val created = HrvstStore(app)
            store = created
            val loaded = created.load()
            meta = loaded.meta
            packets = loaded.packets
            unreadable = loaded.unreadable
            ownerField = loaded.meta?.ownerLabel.orEmpty()
            retentionField = (loaded.meta?.retentionDays ?: 30).toString()
            loaded.meta?.let { applyRetention(it) }
        } catch (_: Exception) {
            fatal = "The keystore key could not be created. Nothing was stored."
        }
    }

    fun show(next: HrvstScreen) {
        if (next != HrvstScreen.Camera) cameraBound = false
        screen = next
    }

    fun permissionDenied() {
        banner = "Camera permission is off. This app will not capture."
    }

    fun saveOwner() {
        val current = store ?: return
        val owner = HrvstRules.cleanOwner(ownerField)
        if (owner == null) {
            banner = "Enter the silo owner's name."
            return
        }
        val days = retentionField.trim().toIntOrNull()?.let(HrvstRules::retentionOrNull)
        if (days == null) {
            banner = "Retention has to be from 1 to 90 days."
            return
        }
        val saved = current.ensureMeta(owner, days)
        meta = saved
        ownerField = saved.ownerLabel
        applyRetention(saved)
        banner = "Personal Silo ready."
    }

    fun openCamera() {
        if (meta == null) {
            banner = "Name the silo owner first."
            return
        }
        cameraBound = false
        screen = HrvstScreen.Camera
    }

    fun open(id: String) {
        openId = id
        recipientField = ""
        includeImage = false
        permitChecked = false
        deleteConfirm = ""
        screen = HrvstScreen.Packet
    }

    fun armShutter(): Boolean {
        if (busy || !cameraBound || meta == null) return false
        busy = true
        return true
    }

    fun onCaptureFailed() {
        busy = false
        banner = "The frame was not captured."
    }

    fun onJpeg(jpeg: ByteArray) {
        val current = store
        val silo = meta
        if (current == null || silo == null) {
            jpeg.fill(0)
            busy = false
            banner = "Name the silo owner first."
            return
        }
        val previous = HrvstRules.previousHash(packets)
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                try {
                    val packet = HrvstRules.packet(
                        id = UUID.randomUUID().toString(),
                        jpeg = jpeg,
                        capturedAtEpochMs = System.currentTimeMillis(),
                        producerVersion = BuildConfig.VERSION_NAME,
                        deviceLabel = Build.MODEL ?: "android",
                        previousContentSha256 = previous,
                    ) ?: return@withContext null
                    current.saveNew(packet, jpeg)
                    packet
                } finally {
                    jpeg.fill(0)
                }
            }
            if (saved == null) {
                banner = "The frame was not kept."
            } else {
                packets = listOf(saved) + packets
                banner = "Saved in the Personal Silo. It is not shared."
                open(saved.id)
            }
            busy = false
        }
    }

    fun prepareShare(context: Context): Intent? {
        val current = store ?: return null
        val packet = openPacket ?: return null
        if (!permitChecked) {
            banner = "Check the permission for this packet before sharing."
            return null
        }
        val updated = HrvstRules.grant(packet, recipientField, includeImage, System.currentTimeMillis())
        if (updated == null) {
            banner = "Name the recipient for this packet."
            return null
        }
        val permit = HrvstRules.permit(updated, recipientField)
        if (permit is SharePermit.Refused) {
            banner = permit.reason
            return null
        }
        current.savePacket(updated)
        packets = packets.map { if (it.id == updated.id) updated else it }
        permitChecked = false
        val allowed = permit as SharePermit.Allowed
        if (!allowed.includeImage) {
            banner = "Sharing the manifest only."
            return Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, allowed.manifest)
            }
        }
        val jpeg = current.readBlob(packet.id)
        if (jpeg == null) {
            banner = "The frame is missing."
            return null
        }
        return try {
            val dir = File(context.cacheDir, "share")
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "${packet.id}.jpg")
            writeAtomic(file, jpeg)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            banner = "Sharing this packet."
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, allowed.manifest)
                clipData = ClipData.newUri(context.contentResolver, "packet", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } finally {
            jpeg.fill(0)
        }
    }

    fun revoke() {
        val current = store ?: return
        val packet = openPacket ?: return
        val updated = HrvstRules.revokeAll(packet)
        current.savePacket(updated)
        packets = packets.map { if (it.id == updated.id) updated else it }
        banner = "Permissions on this packet were revoked."
    }

    fun deleteOpen() {
        val current = store ?: return
        val id = openId ?: return
        if (deleteConfirm.trim() != "DELETE") {
            banner = "Type DELETE to remove this packet."
            return
        }
        current.delete(id)
        packets = packets.filter { it.id != id }
        openId = null
        deleteConfirm = ""
        banner = "Packet deleted."
        screen = HrvstScreen.Home
    }

    fun deleteAll() {
        val current = store ?: return
        if (deleteConfirm.trim() != "DELETE") {
            banner = "Type DELETE to erase the Personal Silo on this phone."
            return
        }
        current.wipe()
        meta = null
        packets = emptyList()
        unreadable = 0
        openId = null
        ownerField = ""
        deleteConfirm = ""
        banner = "Personal Silo deleted on this phone."
        screen = HrvstScreen.Home
    }

    private fun applyRetention(silo: SiloMeta) {
        val current = store ?: return
        val kept = HrvstRules.retain(packets, silo.retentionDays, System.currentTimeMillis())
        val keepIds = kept.map { it.id }.toSet()
        packets.filter { it.id !in keepIds }.forEach { current.delete(it.id) }
        if (kept.size != packets.size) {
            packets = kept
            banner = "Packets past the retention window were deleted."
        }
    }
}
