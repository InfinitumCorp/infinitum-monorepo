package com.infinitum.eidetic.cam

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infinitum.throne.core.cam.EditionTitle
import com.infinitum.throne.core.eidetic.CaptureFlow
import com.infinitum.throne.core.eidetic.DeskUrl
import com.infinitum.throne.core.eidetic.EideticDesk
import com.infinitum.throne.core.eidetic.EnrollmentBook
import com.infinitum.throne.core.eidetic.EnrollmentDraft
import com.infinitum.throne.core.eidetic.EnrolledSubject
import com.infinitum.throne.core.eidetic.Gate
import com.infinitum.throne.core.eidetic.LocalReceipt
import com.infinitum.throne.core.eidetic.SubmitResult
import com.infinitum.throne.core.eidetic.cleanPurpose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class EideticScreen { Home, Enroll, Subjects, Capture, Camera, Queue, Desk, Delete }

class EideticViewModel(app: Application) : AndroidViewModel(app) {
    val editionTitle = EditionTitle.title(BuildConfig.EDITION)
    var screen by mutableStateOf(EideticScreen.Home)
        private set
    var banner by mutableStateOf<String?>(null)
        private set
    var fatal by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var cameraBound by mutableStateOf(false)
    var subjects by mutableStateOf<List<EnrolledSubject>>(emptyList())
        private set
    var unreadable by mutableStateOf(0)
        private set
    var receipts by mutableStateOf<List<LocalReceipt>>(emptyList())
        private set
    var deskField by mutableStateOf(EideticStore.DEFAULT_DESK)
    var name by mutableStateOf("")
    var purposesText by mutableStateOf("")
    var retentionText by mutableStateOf("7")
    var acknowledgment by mutableStateOf("")
    var consentOnFile by mutableStateOf(false)
    var selectedId by mutableStateOf<String?>(null)
    var selectedPurpose by mutableStateOf("")
    var personInFrame by mutableStateOf(false)
    var deleteConfirm by mutableStateOf("")

    private var store: EideticStore? = null
    private var book = EnrollmentBook()

    val selected: EnrolledSubject? get() = selectedId?.let { book.get(it) }

    init {
        try {
            val created = EideticStore(app)
            store = created
            val loaded = created.load()
            book = loaded.book
            unreadable = loaded.unreadable
            subjects = book.snapshot()
            val stored = created.loadReceipts()
            val ids = subjects.map { it.id }.toSet()
            receipts = stored.filter { it.subjectId in ids }
            if (receipts.size != stored.size) created.saveReceipts(receipts)
            deskField = created.deskUrl()
            sweep()
        } catch (_: Exception) {
            fatal = "The keystore key could not be created. Nothing was stored."
        }
    }

    fun show(next: EideticScreen) {
        if (next != EideticScreen.Camera) cameraBound = false
        personInFrame = false
        screen = next
    }

    fun permissionDenied() {
        banner = "Camera permission is off. This app will not capture."
    }

    fun enroll() {
        val current = store ?: return
        val days = retentionText.trim().toIntOrNull()
        if (days == null) {
            banner = "Retention has to be from 1 to 30 days."
            return
        }
        val purposes = purposesText.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        val gate = book.enroll(
            EnrollmentDraft(name, purposes, days, consentOnFile, acknowledgment),
            System.currentTimeMillis(),
            UUID.randomUUID().toString(),
        )
        if (gate is Gate.Closed) {
            banner = gate.reason
            return
        }
        current.save(book)
        subjects = book.snapshot()
        name = ""
        purposesText = ""
        acknowledgment = ""
        consentOnFile = false
        banner = "Enrollment recorded on this phone."
        screen = EideticScreen.Subjects
    }

    fun select(id: String) {
        selectedId = id
        val purposes = book.get(id)?.consent?.purposes.orEmpty()
        if (selectedPurpose !in purposes) selectedPurpose = ""
        personInFrame = false
        screen = EideticScreen.Capture
    }

    fun openCamera() {
        val id = selectedId
        if (id == null) {
            banner = "Choose an enrolled person."
            return
        }
        sweep()
        val gate = book.mayOpenCamera(id, System.currentTimeMillis())
        if (gate is Gate.Closed) {
            banner = gate.reason
            return
        }
        val purpose = cleanPurpose(selectedPurpose)
        if (purpose == null || purpose !in book.get(id)?.consent?.purposes.orEmpty()) {
            banner = "Choose a purpose from the written consent."
            return
        }
        personInFrame = false
        cameraBound = false
        screen = EideticScreen.Camera
    }

    fun armShutter(): Boolean {
        if (busy || !cameraBound || !personInFrame) return false
        busy = true
        personInFrame = false
        return true
    }

    fun onCaptureFailed() {
        busy = false
        banner = "The frame was not captured."
    }

    fun onJpeg(jpeg: ByteArray, frameIsSubject: Boolean) {
        val subjectId = selectedId
        val purpose = selectedPurpose
        val urlText = deskField
        if (subjectId == null) {
            jpeg.fill(0)
            busy = false
            banner = "Choose an enrolled person."
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val parsed = DeskUrl.parse(urlText)
                        ?: return@withContext SubmitResult.Stopped("The desk URL has to be http://127.0.0.1 or http://localhost with a port.")
                    CaptureFlow.submit(
                        EideticDesk(parsed),
                        book,
                        subjectId,
                        purpose,
                        frameIsSubject,
                        jpeg,
                        System.currentTimeMillis(),
                    )
                } finally {
                    jpeg.fill(0)
                }
            }
            when (result) {
                is SubmitResult.Queued -> {
                    receipts = listOf(result.receipt) + receipts
                    store?.saveReceipts(receipts)
                    banner = "Queued for a person to review. ${result.receipt.queueId}. This app did not take an action."
                    cameraBound = false
                    screen = EideticScreen.Queue
                }
                is SubmitResult.Stopped -> banner = result.reason
            }
            busy = false
        }
    }

    fun refreshQueue() {
        val parsed = DeskUrl.parse(deskField)
        if (parsed == null) {
            banner = "The desk URL has to be http://127.0.0.1 or http://localhost with a port."
            return
        }
        val current = receipts
        viewModelScope.launch {
            busy = true
            val updated = withContext(Dispatchers.IO) {
                val desk = EideticDesk(parsed)
                current.map { receipt ->
                    val status = try {
                        desk.queueStatus(receipt.queueId)
                    } catch (_: Exception) {
                        null
                    }
                    if (status == null) receipt else receipt.copy(status = status)
                }
            }
            receipts = updated
            store?.saveReceipts(updated)
            busy = false
            banner = "Queue refreshed. This app does not act on a status."
        }
    }

    fun saveDesk() {
        val parsed = DeskUrl.parse(deskField)
        if (parsed == null) {
            banner = "The desk URL has to be http://127.0.0.1 or http://localhost with a port."
            return
        }
        deskField = parsed.origin
        store?.saveDesk(parsed.origin)
        banner = "Desk set to ${parsed.origin}."
    }

    fun deleteSubject(subject: EnrolledSubject) {
        if (deleteConfirm.trim() != subject.legalName) {
            banner = "Type this person's name to delete the record."
            return
        }
        val current = store ?: return
        viewModelScope.launch {
            busy = true
            val remote = withContext(Dispatchers.IO) { remoteDelete(subject.id) }
            book.delete(subject.id)
            current.save(book)
            subjects = book.snapshot()
            receipts = receipts.filter { it.subjectId != subject.id }
            current.saveReceipts(receipts)
            if (selectedId == subject.id) selectedId = null
            deleteConfirm = ""
            busy = false
            banner = if (remote) {
                "Deleted on this phone. The desk confirmed deletion."
            } else {
                "Deleted on this phone. The desk did not confirm deletion."
            }
        }
    }

    fun deleteAll() {
        if (deleteConfirm.trim() != "DELETE") {
            banner = "Type DELETE to erase enrollments and receipts on this phone."
            return
        }
        val current = store ?: return
        val ids = book.snapshot().map { it.id }
        viewModelScope.launch {
            busy = true
            val confirmed = withContext(Dispatchers.IO) { ids.count { remoteDelete(it) } }
            book.deleteAll()
            current.wipe()
            subjects = emptyList()
            receipts = emptyList()
            unreadable = 0
            selectedId = null
            deleteConfirm = ""
            busy = false
            banner = "Local data deleted. The desk confirmed $confirmed of ${ids.size}."
        }
    }

    private fun sweep() {
        val current = store ?: return
        val removed = book.sweep(System.currentTimeMillis()).toSet()
        if (removed.isEmpty()) return
        current.save(book)
        subjects = book.snapshot()
        receipts = receipts.filter { it.subjectId !in removed }
        current.saveReceipts(receipts)
        if (selectedId in removed) selectedId = null
        banner = "A written-consent window ended. Those records were deleted."
    }

    private fun remoteDelete(id: String): Boolean {
        val parsed = DeskUrl.parse(deskField) ?: return false
        return try {
            EideticDesk(parsed).deleteSubject(id).confirmed
        } catch (_: Exception) {
            false
        }
    }
}
