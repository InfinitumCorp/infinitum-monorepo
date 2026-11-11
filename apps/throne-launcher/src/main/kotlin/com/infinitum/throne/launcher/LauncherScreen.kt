package com.infinitum.throne.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.infinitum.throne.core.catalog.ProductCatalog
import com.infinitum.throne.core.home.HomePage
import com.infinitum.throne.ui.HydratedTile
import com.infinitum.throne.ui.ThroneInk
import com.infinitum.throne.ui.ThroneMuted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val Brand = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, letterSpacing = 0.sp)
private val SearchFill = Color(0xFFF3F3F5)
private val DockFill = Color(0xE8E4E4E9)
private val DockShape = RoundedCornerShape(36.dp)
private val TileSize = 60.dp

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
            it.label.contains(query, ignoreCase = true) ||
                it.packageName?.contains(query, ignoreCase = true) == true
        }
    }
    val infinitumGrid = rememberLazyGridState()
    val othersGrid = rememberLazyGridState()
    val searchGrid = rememberLazyGridState()
    val pagerState = rememberPagerState(pageCount = { 2 })
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (state.showAbout) {
            AboutScreen(
                edition = state.edition.label,
                onBack = viewModel::closeAbout,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 108.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "THRONE][OS™",
                    style = Brand.copy(fontWeight = FontWeight.Light, fontSize = 15.sp, letterSpacing = 0.4.sp),
                    color = ThroneInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = viewModel::openAbout),
                )
                Text(
                    text = state.edition.label,
                    style = Brand.copy(fontSize = 12.sp),
                    color = ThroneMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                )
                Spacer(Modifier.height(16.dp))
                SearchField(value = state.query, onValueChange = viewModel::onQuery)
                Spacer(Modifier.height(14.dp))
                if (query.isNotEmpty()) {
                    Box(Modifier.weight(1f)) {
                        AppGrid(
                            apps = results,
                            state = searchGrid,
                            empty = "No matching apps.",
                            showPage = true,
                            onOpen = { openApp(context, it) },
                            onInfo = { openInfo(context, it) },
                        )
                    }
                } else {
                    val scope = rememberCoroutineScope()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        PageTab("INFINITUM", pagerState.currentPage == 0) {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    0,
                                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                                )
                            }
                        }
                        Spacer(Modifier.size(28.dp))
                        PageTab("OTHERS", pagerState.currentPage == 1) {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    1,
                                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                                )
                            }
                        }
                    }
                    PageDots(pagerState.currentPage, Modifier.padding(top = 10.dp, bottom = 4.dp))
                    Box(Modifier.weight(1f)) {
                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                            val apps = if (page == 0) state.infinitum else state.others
                            val empty = if (state.loading) {
                                "Reading apps."
                            } else if (page == 0) {
                                "The product catalog is empty."
                            } else {
                                "No other launchable apps."
                            }
                            AppGrid(
                                apps = apps,
                                state = if (page == 0) infinitumGrid else othersGrid,
                                empty = empty,
                                showPage = false,
                                onOpen = { openApp(context, it) },
                                onInfo = { openInfo(context, it) },
                            )
                        }
                    }
                }
            }
        }
        FrostedCallDock(
            messages = state.messages,
            onCall = { openDialer(context) },
            onMessages = { message -> openMessages(context, message) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun AboutScreen(edition: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 28.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            painter = painterResource(com.infinitum.throne.ui.R.drawable.infinitum_logo_stacked),
            contentDescription = "Infinitum Corporation",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp),
        )
        Text(
            "THRONE][OS™",
            style = Brand.copy(fontWeight = FontWeight.Light, fontSize = 22.sp),
            textAlign = TextAlign.Center,
        )
        Text(edition, style = Brand.copy(fontSize = 15.sp), color = ThroneMuted)
        Text(
            "Debug build. Not a certified release.",
            style = Brand.copy(fontSize = 14.sp),
            color = ThroneMuted,
            textAlign = TextAlign.Center,
        )
        Text(
            "No network. No widgets. No ads. No analytics.",
            style = Brand.copy(fontSize = 13.sp),
            color = ThroneMuted,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Back",
            style = Brand.copy(fontSize = 16.sp),
            color = ThroneInk,
            modifier = Modifier
                .padding(top = 8.dp)
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun FrostedCallDock(
    messages: MessageDock?,
    onCall: () -> Unit,
    onMessages: (MessageDock) -> Unit,
    modifier: Modifier = Modifier,
) {
    val absorb = remember { MutableInteractionSource() }
    Box(
        modifier
            .padding(horizontal = 22.dp)
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .height(96.dp)
            .drawBehind { drawDockShadow() }
            .background(DockFill, DockShape)
            .border(0.75.dp, Color.Black.copy(alpha = 0.08f), DockShape)
            .clickable(interactionSource = absorb, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        val button = 58.dp
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (messages != null) {
                Spacer(Modifier.size(button))
                Spacer(Modifier.size(18.dp))
            }
            CallButton(onCall)
            if (messages != null) {
                Spacer(Modifier.size(18.dp))
                MessagesButton(onClick = { onMessages(messages) })
            }
        }
    }
}

private fun DrawScope.drawDockShadow() {
    val corner = 36.dp.toPx()
    val blur = 14.dp.toPx()
    val steps = 8
    for (step in steps downTo 1) {
        val spread = blur * step / steps
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.12f / steps),
            topLeft = Offset(-spread, 2.dp.toPx()),
            size = Size(size.width + spread * 2f, size.height + spread),
            cornerRadius = CornerRadius(corner + spread, corner + spread),
        )
    }
}

@Composable
private fun MessagesButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .shadow(8.dp, CircleShape, ambientColor = Color(0x22000000), spotColor = Color(0x18000000))
            .background(ThroneInk, CircleShape)
            .semantics { contentDescription = "Messages" }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(size.width * 0.08f, size.height * 0.08f),
                size = Size(size.width * 0.84f, size.height * 0.62f),
                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
            )
            val tail = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.22f, size.height * 0.66f)
                lineTo(size.width * 0.16f, size.height * 0.94f)
                lineTo(size.width * 0.46f, size.height * 0.66f)
                close()
            }
            drawPath(tail, Color.White)
        }
    }
}

@Composable
private fun CallButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .shadow(8.dp, CircleShape, ambientColor = Color(0x22000000), spotColor = Color(0x18000000))
            .background(ThroneInk, CircleShape)
            .semantics { contentDescription = "Call" }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val stroke = Stroke(width = 1.7.dp.toPx())
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(size.width * 0.28f, size.height * 0.06f),
                size = Size(size.width * 0.44f, size.height * 0.88f),
                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                style = stroke,
            )
            drawCircle(
                color = Color.White,
                radius = 1.5.dp.toPx(),
                center = Offset(size.width / 2f, size.height * 0.78f),
            )
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search", color = ThroneMuted, style = Brand.copy(fontSize = 16.sp)) },
        singleLine = true,
        textStyle = Brand.copy(fontSize = 16.sp, color = ThroneInk),
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SearchFill,
            unfocusedContainerColor = SearchFill,
            disabledContainerColor = SearchFill,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = ThroneInk,
            focusedTextColor = ThroneInk,
            unfocusedTextColor = ThroneInk,
        ),
        keyboardOptions = KeyboardOptions.Default,
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
    )
}

@Composable
private fun PageTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(
        targetValue = if (selected) ThroneInk else ThroneMuted,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "page-tab",
    )
    Text(
        text = label,
        style = Brand.copy(fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, fontSize = 13.sp),
        color = color,
        modifier = Modifier
            .pointerInput(label) { detectTapGestures(onTap = { onClick() }) }
            .padding(vertical = 6.dp),
    )
}

@Composable
private fun PageDots(page: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(2) { index ->
            val selected = page == index
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (selected) 7.dp else 5.dp)
                    .background(if (selected) ThroneInk else Color(0xFFD5D5D8), CircleShape),
            )
        }
    }
}

@Composable
private fun AppGrid(
    apps: List<AppEntry>,
    state: LazyGridState,
    empty: String,
    showPage: Boolean,
    onOpen: (AppEntry) -> Unit,
    onInfo: (AppEntry) -> Unit,
    interactive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val mirror = rememberLazyGridState()
    val gridState = if (interactive) state else mirror
    if (!interactive) {
        LaunchedEffect(state) {
            androidx.compose.runtime.snapshotFlow {
                state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
            }.distinctUntilChanged().collect { (index, offset) ->
                gridState.scrollToItem(index, offset)
            }
        }
    }
    if (apps.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (empty.isNotEmpty()) {
                Text(
                    empty,
                    style = Brand.copy(fontSize = 15.sp),
                    color = ThroneMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 128.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        items(apps, key = { it.key }) { app ->
            AppTile(app, showPage, interactive, onOpen, onInfo)
        }
    }
}

@Composable
private fun AppTile(
    app: AppEntry,
    showPage: Boolean,
    interactive: Boolean,
    onOpen: (AppEntry) -> Unit,
    onInfo: (AppEntry) -> Unit,
) {
    val scale = remember(app.key) { Animatable(1f) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                clip = false
            }
            .then(
                if (!interactive) {
                    Modifier
                } else {
                    Modifier.pointerInput(app.key) {
                        detectTapGestures(
                            onPress = {
                                scale.animateTo(0.9f, spring(dampingRatio = 0.7f, stiffness = 700f))
                                tryAwaitRelease()
                                scale.animateTo(
                                    1f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                )
                            },
                            onTap = { onOpen(app) },
                            onLongPress = { onInfo(app) },
                        )
                    }
                },
            ),
    ) {
        HydratedTile(
            productId = app.productId,
            fallback = app.icon,
            contentDescription = app.label,
            colorIcon = app.colorIcon,
            modifier = Modifier.size(TileSize),
        )
        Text(
            text = app.label,
            style = Brand.copy(fontSize = 11.sp),
            color = ThroneInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth(),
        )
        if (showPage) {
            Text(
                text = if (app.page == HomePage.INFINITUM) "INFINITUM" else "OTHERS",
                style = Brand.copy(fontSize = 10.sp),
                color = ThroneMuted,
                maxLines = 1,
            )
        }
    }
}

private fun openMessages(context: android.content.Context, message: MessageDock) {
    val launch = context.packageManager.getLaunchIntentForPackage(message.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (launch != null) {
        try {
            context.startActivity(launch)
            return
        } catch (_: ActivityNotFoundException) {
            // Fall through to the smsto handler.
        }
    }
    val sendTo = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
        .setPackage(message.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(sendTo)
    } catch (_: ActivityNotFoundException) {
        // No messaging app can be opened.
    }
}

private fun openDialer(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No dialer is installed.
    }
}

private fun openApp(context: android.content.Context, app: AppEntry) {
    if (app.kind == EntryKind.NATIVE && app.packageName != null && app.className != null) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.className)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
        }
    }
    val productId = app.productId ?: return
    val intent = Intent()
        .setClassName(ProductCatalog.SUITE_PACKAGE, ProductCatalog.SUITE_ACTIVITY)
        .putExtra(ProductCatalog.EXTRA_PRODUCT_ID, productId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // The suite is installed with this layer. Without it, a site cannot open.
    }
}

private fun openInfo(context: android.content.Context, app: AppEntry) {
    val pkg = app.packageName ?: return
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:$pkg"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
