package app.infinitum.nether

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import app.infinitum.nether.core.NetherEntry
import app.infinitum.nether.ui.NetherSession
import java.net.URI

class NetherAutofillService : AutofillService() {
    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }
        val fields = collect(structure)
        if (fields.isEmpty()) {
            callback.onSuccess(null)
            return
        }
        if (!NetherSession.unlocked) {
            pending = structure
            val ids = fields.map { it.id }.toTypedArray()
            val flags = if (Build.VERSION.SDK_INT >= 31) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                1,
                Intent(this, MainActivity::class.java).putExtra(EXTRA_AUTH, true),
                flags,
            )
            val response = FillResponse.Builder()
                .setAuthentication(ids, pendingIntent.intentSender, chip(this, getString(R.string.autofill_unlock)))
                .build()
            callback.onSuccess(response)
            return
        }
        callback.onSuccess(responseFor(this, structure))
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    companion object {
        const val EXTRA_AUTH = "nether.autofill.auth"
        @Volatile var pending: AssistStructure? = null

        fun responseFor(service: android.content.Context, structure: AssistStructure): FillResponse? {
            val fields = collect(structure)
            val document = NetherSession.document ?: return null
            if (fields.isEmpty()) return null
            val domain = fields.firstNotNullOfOrNull { it.webDomain }?.lowercase()
            val matches = document.entries.filter { entry ->
                entry.secret.isNotEmpty() && (domain == null || entry.url.contains(domain, ignoreCase = true) || host(entry.url) == domain)
            }.ifEmpty { document.entries.filter { it.secret.isNotEmpty() } }.take(8)
            if (matches.isEmpty()) return null
            val builder = FillResponse.Builder()
            for (entry in matches) {
                builder.addDataset(dataset(service, entry, fields))
            }
            return builder.build()
        }

        private fun dataset(context: android.content.Context, entry: NetherEntry, fields: List<Field>): Dataset {
            val views = chip(context, entry.title.ifBlank { entry.username.ifBlank { "Nether" } })
            val builder = Dataset.Builder(views)
            for (field in fields) {
                val value = when (field.kind) {
                    Kind.PASSWORD -> entry.secret
                    Kind.USERNAME -> entry.username.ifBlank { entry.secret }
                }
                builder.setValue(field.id, AutofillValue.forText(value))
            }
            return builder.build()
        }

        private fun chip(context: android.content.Context, label: String): RemoteViews {
            return RemoteViews(context.packageName, R.layout.autofill_chip).apply {
                setTextViewText(R.id.label, label)
            }
        }

        private fun host(url: String): String? = try {
            URI(url).host?.lowercase()
        } catch (_: Exception) {
            null
        }

        private fun collect(structure: AssistStructure): List<Field> {
            val out = ArrayList<Field>()
            for (w in 0 until structure.windowNodeCount) {
                walk(structure.getWindowNodeAt(w).rootViewNode, out)
            }
            return out
        }

        private fun walk(node: AssistStructure.ViewNode, out: MutableList<Field>) {
            val id = node.autofillId
            val kind = kindOf(node)
            if (id != null && kind != null) {
                out += Field(id, kind, node.webDomain)
            }
            for (i in 0 until node.childCount) walk(node.getChildAt(i), out)
        }

        private fun kindOf(node: AssistStructure.ViewNode): Kind? {
            val hints = node.autofillHints?.joinToString(" ")?.lowercase().orEmpty()
            val hint = node.hint?.lowercase().orEmpty()
            val entry = node.idEntry?.lowercase().orEmpty()
            val html = node.htmlInfo?.attributes?.joinToString(" ") { "${it.first} ${it.second}" }?.lowercase().orEmpty()
            val hay = "$hints $hint $entry $html"
            return when {
                hay.contains("password") || hay.contains("current-password") -> Kind.PASSWORD
                hay.contains("username") || hay.contains("email") || hay.contains("user") -> Kind.USERNAME
                else -> null
            }
        }
    }
}

private enum class Kind { USERNAME, PASSWORD }

private data class Field(val id: AutofillId, val kind: Kind, val webDomain: String?)
