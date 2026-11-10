package com.infinitum.throne.guard

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.infinitum.throne.core.audit.Finding
import com.infinitum.throne.core.audit.FindingStatus
import com.infinitum.throne.core.audit.InstalledApp
import com.infinitum.throne.core.net.PrivateDnsPolicy
import com.infinitum.throne.ui.ThroneButton
import com.infinitum.throne.ui.ThroneCard
import com.infinitum.throne.ui.ThroneMuted
import com.infinitum.throne.ui.ThroneSection
import com.infinitum.throne.ui.Wordmark
import com.infinitum.throne.ui.throneFieldColors

@Composable
fun GuardScreen(viewModel: GuardViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (viewModel.screen == GuardScreen.DnsGuide) {
        DnsGuide(viewModel)
    } else {
        Dashboard(viewModel)
    }
}

@Composable
private fun Dashboard(viewModel: GuardViewModel) {
    val context = LocalContext.current
    val report = viewModel.report
    val messaging = report?.findings?.filter { it.id.startsWith("msg_") }.orEmpty()
    val device = report?.findings?.filter { !it.id.startsWith("msg_") }.orEmpty()
    val query = viewModel.query.trim()
    val apps = report?.permissionApps.orEmpty().filter { app ->
        query.isEmpty() ||
            app.label.contains(query, true) ||
            app.packageName.contains(query, true) ||
            app.grantedDangerous.any { it.contains(query, true) }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Wordmark(edition = viewModel.edition.name, kicker = "Guard")
            Text(
                text = "Local audit. Nothing on this screen is enforced. Links open Android settings so you can change them.",
                style = MaterialTheme.typography.bodyMedium,
                color = ThroneMuted,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        item { ThroneSection("SECURE MESSAGING") }
        if (messaging.isEmpty()) {
            item { Text(if (viewModel.loading) "Reading this device." else "No messaging checks.", color = ThroneMuted) }
        }
        items(messaging, key = { it.id }) { finding ->
            FindingCard(finding) { destination ->
                if (destination == "private_dns_guide") viewModel.showGuide()
                else GuardIntents.open(context, destination)
            }
        }
        item { ThroneSection("DEVICE") }
        items(device, key = { it.id }) { finding ->
            FindingCard(finding) { destination ->
                if (destination == "private_dns_guide") viewModel.showGuide()
                else GuardIntents.open(context, destination)
            }
        }
        item { ThroneSection("CAMERA") }
        item { SensorBlock(report?.camera.orEmpty(), "No app has camera permission granted.") { GuardIntents.open(context, "app:$it") } }
        item { ThroneSection("MICROPHONE") }
        item { SensorBlock(report?.microphone.orEmpty(), "No app has microphone permission granted.") { GuardIntents.open(context, "app:$it") } }
        item { ThroneSection("LOCATION") }
        item { SensorBlock(report?.location.orEmpty(), "No app has location permission granted.") { GuardIntents.open(context, "app:$it") } }
        item { ThroneSection("PERMISSIONS") }
        item {
            OutlinedTextField(
                value = viewModel.query,
                onValueChange = { viewModel.query = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Filter by app or permission", color = ThroneMuted) },
                singleLine = true,
                colors = throneFieldColors(),
                keyboardOptions = KeyboardOptions.Default,
            )
        }
        items(apps, key = { "perm-${it.packageName}" }) { app ->
            ThroneCard {
                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = ThroneMuted)
                Text(
                    text = if (app.grantedDangerous.isEmpty()) {
                        "No dangerous permissions granted."
                    } else {
                        app.grantedDangerous.joinToString("  ") { it.removePrefix("android.permission.") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                ThroneButton(
                    text = "App settings",
                    onClick = { GuardIntents.open(context, "app:${app.packageName}") },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        item {
            ThroneButton(text = "Refresh", onClick = viewModel::refresh, modifier = Modifier.padding(top = 8.dp))
            Text(
                text = "No analytics. The only network call is the Private DNS check you start yourself.",
                style = MaterialTheme.typography.bodySmall,
                color = ThroneMuted,
                modifier = Modifier.padding(top = 16.dp, bottom = 28.dp),
            )
        }
    }
}

@Composable
private fun FindingCard(finding: Finding, onOpen: (String) -> Unit) {
    ThroneCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(finding.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                finding.status.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (finding.status == FindingStatus.REVIEW) FontWeight.Medium else FontWeight.Normal,
            )
        }
        Text(
            finding.summary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 10.dp),
        )
        finding.rows.forEach { row ->
            val destination = row.destination
            Text(row.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
            Text(row.detail, style = MaterialTheme.typography.bodySmall, color = ThroneMuted)
            if (destination != null) {
                ThroneButton(
                    text = "Open",
                    onClick = { onOpen(destination) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        val actionLabel = finding.actionLabel
        val actionDestination = finding.actionDestination
        if (actionLabel != null && actionDestination != null) {
            ThroneButton(
                text = actionLabel,
                onClick = { onOpen(actionDestination) },
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun SensorBlock(apps: List<InstalledApp>, empty: String, onOpen: (String) -> Unit) {
    if (apps.isEmpty()) {
        Text(empty, style = MaterialTheme.typography.bodyMedium, color = ThroneMuted)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        apps.forEach { app ->
            ThroneCard {
                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = ThroneMuted)
                ThroneButton(
                    text = "App settings",
                    onClick = { onOpen(app.packageName) },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun DnsGuide(viewModel: GuardViewModel) {
    val context = LocalContext.current
    val specifier = viewModel.snapshot?.privateDnsSpecifier?.trim().orEmpty()
    val mode = viewModel.snapshot?.privateDnsMode ?: "unread"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Wordmark(edition = viewModel.edition.name, kicker = "Private DNS")
        Text(
            "Android does not let this app set Private DNS. You type the hostname. Nothing is contacted until you tap a check.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("Current mode: $mode", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Current hostname: ${specifier.ifEmpty { "none" }}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "1. Open network settings.\n2. On HiOS: Network and internet, then Private DNS.\n3. Choose Private DNS provider hostname.\n4. Enter ${PrivateDnsPolicy.RECOMMENDED_HOST}\n5. Return here and refresh.\n\n${PrivateDnsPolicy.RECOMMENDED_HOST} is a public tracker-blocking resolver, not an Infinitum server. dns.quad9.net is another public choice. Use VEIL’s own resolver when you have its hostname. Private DNS is not a substitute for always-on VEIL with lockdown.",
            style = MaterialTheme.typography.bodyMedium,
        )
        ThroneButton(text = "Open network settings", onClick = { GuardIntents.open(context, "wireless") })
        ThroneButton(
            text = if (viewModel.probing) "Checking" else "Check recommended resolver",
            onClick = viewModel::probeRecommended,
            enabled = !viewModel.probing,
        )
        if (specifier.isNotEmpty()) {
            ThroneButton(
                text = "Check the hostname set on this phone",
                onClick = viewModel::probeConfigured,
                enabled = !viewModel.probing,
            )
        }
        viewModel.probeMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        ThroneButton(text = "Back", onClick = viewModel::showDashboard)
        Spacer(Modifier.height(12.dp))
    }
}
