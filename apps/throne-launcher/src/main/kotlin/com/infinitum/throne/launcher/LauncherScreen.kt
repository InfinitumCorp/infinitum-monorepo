package com.infinitum.throne.launcher

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.infinitum.throne.core.home.Edition
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
    val focus = LocalFocusManager.current
    var searchFocused by androidx.compose.runtime.mutableStateOf(false)
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val contactsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.onContactsGranted() else viewModel.dismissContactsPrompt()
    }
    BackHandler(enabled = query.isNotEmpty() && !state.showAbout) {
        viewModel.onQuery("")
        focus.clearFocus()
    }
    val infinitumGrid = rememberLazyGridState()
    val othersGrid = rememberLazyGridState()
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
                    style = Brand.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.4.sp),
                    color = ThroneInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = viewModel::openAbout),
                )
                Text(
                    text = if (state.edition == Edition.KETER) "^KETERv1" else state.edition.label,
                    style = Brand.copy(fontSize = 12.sp),
                    color = ThroneMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                )
                Spacer(Modifier.height(16.dp))
                SearchField(
                    value = state.query,
                    onValueChange = viewModel::onQuery,
                    onFocus = { searchFocused = it },
                    onClear = {
                        viewModel.onQuery("")
                        searchFocused = false
                        focus.clearFocus()
                    },
                    onSearch = {
                        val exact = PhoneSearch.exactApp(state.hits.apps, query)
                        if (exact != null) openApp(context, exact) else openVeil(context, query)
                        focus.clearFocus()
                    },
                )
                Spacer(Modifier.height(14.dp))
                if (query.isNotEmpty()) {
                    SearchResults(
                        query = query,
                        hits = state.hits,
                        onApp = { openApp(context, it) },
                        onVeil = { openVeil(context, query) },
                        onContact = { openContact(context, it) },
                        onCall = { openTel(context, it) },
                        onMessage = { openSms(context, it) },
                        onSetting = { openSetting(context, it) },
                        onFile = { openFile(context, it) },
                        onAllowContacts = { contactsPermission.launch(Manifest.permission.READ_CONTACTS) },
                        modifier = Modifier.weight(1f),
                    )
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
        if (query.isEmpty() && !searchFocused && !imeVisible) FrostedCallDock(
            messages = state.messages,
            onCall = { openDialer(context) },
            onCamera = { openCamera(context) },
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
    onCamera: () -> Unit,
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
            CameraButton(onCamera)
            Spacer(Modifier.size(18.dp))
            CallButton(onCall)
            Spacer(Modifier.size(18.dp))
            if (messages != null) {
                MessagesButton(onClick = { onMessages(messages) })
            } else {
                Spacer(Modifier.size(button))
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
private fun CameraButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .shadow(8.dp, CircleShape, ambientColor = Color(0x22000000), spotColor = Color(0x18000000))
            .background(ThroneInk, CircleShape)
            .semantics { contentDescription = "Camera" }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val stroke = Stroke(width = 1.7.dp.toPx())
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(size.width * 0.06f, size.height * 0.28f),
                size = Size(size.width * 0.88f, size.height * 0.58f),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                style = stroke,
            )
            drawCircle(
                color = Color.White,
                radius = size.minDimension * 0.16f,
                center = Offset(size.width / 2f, size.height * 0.58f),
                style = stroke,
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(size.width * 0.34f, size.height * 0.14f),
                size = Size(size.width * 0.22f, size.height * 0.14f),
                cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
            )
        }
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
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { onFocus(it.isFocused) },
        placeholder = { Text("Search", color = ThroneMuted, style = Brand.copy(fontSize = 16.sp)) },
        singleLine = true,
        textStyle = Brand.copy(fontSize = 16.sp, color = ThroneInk),
        shape = RoundedCornerShape(14.dp),
        trailingIcon = {
            if (value.isNotEmpty()) {
                Text(
                    "Clear",
                    style = Brand.copy(fontSize = 13.sp),
                    color = ThroneMuted,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clickable(onClick = onClear),
                )
            }
        },
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
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
    )
}

@Composable
private fun SearchResults(
    query: String,
    hits: SearchHits,
    onApp: (AppEntry) -> Unit,
    onVeil: () -> Unit,
    onContact: (ContactHit) -> Unit,
    onCall: (ContactHit) -> Unit,
    onMessage: (ContactHit) -> Unit,
    onSetting: (SettingHit) -> Unit,
    onFile: (FileHit) -> Unit,
    onAllowContacts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "veil") {
            Text(
                text = "Search with VEIL — \"$query\"",
                style = Brand.copy(fontSize = 15.sp),
                color = ThroneInk,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onVeil)
                    .padding(vertical = 12.dp),
            )
        }
        if (hits.apps.isNotEmpty()) {
            item(key = "apps-label") { SectionLabel("Apps") }
            items(hits.apps, key = { "app:${it.key}" }) { app ->
                ResultRow(
                    title = app.label,
                    icon = {
                        HydratedTile(
                            productId = app.productId,
                            fallback = app.icon,
                            contentDescription = app.label,
                            colorIcon = app.colorIcon,
                            modifier = Modifier.size(40.dp),
                        )
                    },
                    onClick = { onApp(app) },
                )
            }
        }
        if (hits.askContacts) {
            item(key = "contacts-ask") {
                ResultRow(
                    title = "Contacts",
                    detail = "Allow to search names and numbers",
                    icon = { LetterMark("C") },
                    onClick = onAllowContacts,
                )
            }
        }
        if (hits.contacts.isNotEmpty()) {
            item(key = "contacts-label") { SectionLabel("Contacts") }
            items(hits.contacts, key = { "contact:${it.id}" }) { contact ->
                ContactRow(contact, onContact, onCall, onMessage)
            }
        }
        if (hits.settings.isNotEmpty()) {
            item(key = "settings-label") { SectionLabel("Settings") }
            items(hits.settings, key = { "setting:${it.action}" }) { setting ->
                ResultRow(
                    title = setting.title,
                    icon = { LetterMark(setting.title.take(1)) },
                    onClick = { onSetting(setting) },
                )
            }
        }
        if (hits.files.isNotEmpty()) {
            item(key = "files-label") { SectionLabel("Files") }
            items(hits.files, key = { "file:${it.uri}" }) { file ->
                ResultRow(
                    title = file.name,
                    icon = { LetterMark("F") },
                    onClick = { onFile(file) },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = Brand.copy(fontSize = 12.sp),
        color = ThroneMuted,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun ResultRow(
    title: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    detail: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = Brand.copy(fontSize = 16.sp), color = ThroneInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) {
                Text(detail, style = Brand.copy(fontSize = 12.sp), color = ThroneMuted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: ContactHit,
    onOpen: (ContactHit) -> Unit,
    onCall: (ContactHit) -> Unit,
    onMessage: (ContactHit) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(contact) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LetterMark(contact.name.take(1).uppercase())
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(contact.name, style = Brand.copy(fontSize = 16.sp), color = ThroneInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!contact.number.isNullOrBlank()) {
                Text(contact.number, style = Brand.copy(fontSize = 12.sp), color = ThroneMuted, maxLines = 1)
            }
        }
        if (!contact.number.isNullOrBlank()) {
            Text(
                "Call",
                style = Brand.copy(fontSize = 13.sp),
                color = ThroneInk,
                modifier = Modifier
                    .clickable { onCall(contact) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Text(
                "Message",
                style = Brand.copy(fontSize = 13.sp),
                color = ThroneInk,
                modifier = Modifier
                    .clickable { onMessage(contact) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun LetterMark(letter: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(22))
            .background(Color.White)
            .border(0.75.dp, Color.Black.copy(alpha = 0.07f), RoundedCornerShape(22)),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, style = Brand.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp), color = ThroneInk)
    }
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

private fun openCamera(context: android.content.Context) {
    val still = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(still)
        return
    } catch (_: ActivityNotFoundException) {
        // Try the default still-capture handler.
    }
    val capture = Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (context.packageManager.resolveActivity(capture, PackageManager.MATCH_DEFAULT_ONLY) != null) {
        try {
            context.startActivity(capture)
            return
        } catch (_: ActivityNotFoundException) {
            // Try a known camera package, then any capture handler.
        }
    }
    val pm = context.packageManager
    val packages = LinkedHashSet<String>()
    packages += "com.transsion.camera"
    pm.queryIntentActivities(capture, PackageManager.MATCH_ALL).forEach { info ->
        packages += info.activityInfo.packageName
    }
    pm.queryIntentActivities(still, PackageManager.MATCH_ALL).forEach { info ->
        packages += info.activityInfo.packageName
    }
    for (pkg in packages) {
        val launch = pm.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: continue
        try {
            context.startActivity(launch)
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next camera.
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

private fun openVeil(context: android.content.Context, query: String) {
    if (query.isBlank()) return
    val uri = PhoneSearch.veilUri(query)
    val targeted = Intent(Intent.ACTION_VIEW, uri)
        .setPackage(PhoneSearch.VEIL_PACKAGE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val veilInstalled = context.packageManager.resolveActivity(targeted, PackageManager.MATCH_DEFAULT_ONLY) != null
    val intent = if (veilInstalled) targeted else Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No browser can take the address.
    }
}

private fun openContact(context: android.content.Context, contact: ContactHit) {
    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contact.id.toString())
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // The contacts app is unavailable.
    }
}

private fun openTel(context: android.content.Context, contact: ContactHit) {
    val number = contact.number ?: return
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No dialer is installed.
    }
}

private fun openSms(context: android.content.Context, contact: ContactHit) {
    val number = contact.number ?: return
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No messaging app is installed.
    }
}

private fun openSetting(context: android.content.Context, setting: SettingHit) {
    val intent = Intent(setting.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // This settings page is not on the device.
    }
}

private fun openFile(context: android.content.Context, file: FileHit) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setData(Uri.parse(file.uri))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Nothing can open this file.
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
    if (app.productId == "metatron" && app.kind != EntryKind.NATIVE) {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse("https://mttrn.app")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(view)
        } catch (_: ActivityNotFoundException) {
            // No browser is available.
        }
        return
    }
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
