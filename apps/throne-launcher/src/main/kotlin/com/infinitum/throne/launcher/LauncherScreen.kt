package com.infinitum.throne.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.infinitum.throne.core.home.HomePage
import com.infinitum.throne.ui.ThroneInk
import com.infinitum.throne.ui.ThroneLine
import com.infinitum.throne.ui.ThroneMuted
import com.infinitum.throne.ui.Wordmark
import com.infinitum.throne.ui.throneFieldColors
import kotlinx.coroutines.launch

@Composable
fun LauncherScreen(viewModel: LauncherViewModel) {
    val state by viewModel.state
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val query = state.query.trim()
    val results = if (query.isEmpty()) {
        emptyList()
    } else {
        (state.infinitum + state.others).filter {
            it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Wordmark(edition = state.edition.name, kicker = "")
        Spacer(Modifier.height(22.dp))
        val focus = LocalFocusManager.current
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search", color = ThroneMuted) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = throneFieldColors(),
            keyboardOptions = KeyboardOptions.Default,
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        )
        Spacer(Modifier.height(18.dp))
        if (query.isNotEmpty()) {
            Text("SEARCH", style = MaterialTheme.typography.titleMedium)
            AppGrid(
                apps = results,
                modifier = Modifier.weight(1f).padding(top = 8.dp),
                empty = "No matching apps.",
                showPage = true,
                onOpen = { openApp(context, it) },
                onInfo = { openInfo(context, it) },
            )
        } else {
            val pagerState = rememberPagerState(pageCount = { 2 })
            val scope = rememberCoroutineScope()
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                PageTab("INFINITUM", pagerState.currentPage == 0) {
                    scope.launch { pagerState.animateScrollToPage(0) }
                }
                PageTab("OTHERS", pagerState.currentPage == 1) {
                    scope.launch { pagerState.animateScrollToPage(1) }
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).padding(top = 8.dp)) { page ->
                val apps = if (page == 0) state.infinitum else state.others
                val empty = if (state.loading) {
                    "Reading apps."
                } else if (page == 0) {
                    "Apps appear here when they are installed."
                } else {
                    "No other launchable apps."
                }
                AppGrid(
                    apps = apps,
                    empty = empty,
                    showPage = false,
                    onOpen = { openApp(context, it) },
                    onInfo = { openInfo(context, it) },
                )
            }
        }
        Text(
            text = "No network. No widgets. No ads. No analytics.",
            style = MaterialTheme.typography.bodySmall,
            color = ThroneMuted,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun PageTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.pointerInput(label) { detectTapGestures(onTap = { onClick() }) }) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (selected) ThroneInk else ThroneMuted)
        Box(
            Modifier
                .padding(top = 8.dp)
                .height(1.dp)
                .width(112.dp)
                .background(if (selected) ThroneInk else ThroneLine),
        )
    }
}

@Composable
private fun AppGrid(
    apps: List<AppEntry>,
    empty: String,
    showPage: Boolean,
    onOpen: (AppEntry) -> Unit,
    onInfo: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (apps.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(empty, style = MaterialTheme.typography.bodyMedium, color = ThroneMuted, textAlign = TextAlign.Center)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(apps, key = { it.key }) { app ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(app.key) {
                        detectTapGestures(
                            onTap = { onOpen(app) },
                            onLongPress = { onInfo(app) },
                        )
                    }
                    .padding(vertical = 6.dp),
            ) {
                if (app.icon != null) {
                    Image(app.icon, contentDescription = app.label, modifier = Modifier.size(52.dp))
                } else {
                    Box(Modifier.size(52.dp).border(1.dp, ThroneLine))
                }
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = ThroneInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (showPage) {
                    Text(
                        text = if (app.page == HomePage.INFINITUM) "INFINITUM" else "OTHERS",
                        style = MaterialTheme.typography.labelSmall,
                        color = ThroneMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun openApp(context: android.content.Context, app: AppEntry) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(app.packageName, app.className)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.packageManager.getLaunchIntentForPackage(app.packageName)?.let(context::startActivity)
    }
}

private fun openInfo(context: android.content.Context, app: AppEntry) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${app.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
