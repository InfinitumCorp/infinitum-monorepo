package com.infinitum.throne.suite

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Message
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.infinitum.throne.core.catalog.Product
import com.infinitum.throne.core.catalog.ProductCatalog
import com.infinitum.throne.core.catalog.ShelfKind
import com.infinitum.throne.core.home.EditionPolicy
import com.infinitum.throne.ui.HydratedTile
import com.infinitum.throne.ui.ThroneButton
import com.infinitum.throne.ui.ThroneCard
import com.infinitum.throne.ui.ThroneMuted
import com.infinitum.throne.ui.ThroneTheme
import com.infinitum.throne.ui.Wordmark

/**
 * Opens a catalog product in this order: the installed native app, then this
 * secure WebView, then a Coming soon page. Trusted Web Activity is not used.
 * No product host published a Digital Asset Link, and a Chrome fallback would
 * leave this no-tracker shell.
 */
class ShellActivity : ComponentActivity() {
    private var products: List<Product> = emptyList()
    private var productId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
            navigationBarStyle = SystemBarStyle.light(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
        )
        val edition = EditionPolicy.parse(BuildConfig.EDITION)
        products = loadProducts().let { ProductCatalog.visible(it, edition) }
        productId = intent.getStringExtra(ProductCatalog.EXTRA_PRODUCT_ID)
        if (productId == "nether") {
            startActivity(Intent(this, NetherActivity::class.java))
            productId = null
        }
        if (openNative(productId)) return
        setContent {
            ThroneTheme {
                SuiteScreen(
                    edition = edition.label,
                    products = products,
                    productId = productId,
                    onOpen = { product ->
                        if (!openNative(product.id)) productId = product.id
                    },
                    onBack = { productId = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val requested = intent.getStringExtra(ProductCatalog.EXTRA_PRODUCT_ID)
        if (requested == "nether") {
            startActivity(Intent(this, NetherActivity::class.java))
            productId = null
            return
        }
        if (!openNative(requested)) productId = requested
    }

    private fun loadProducts(): List<Product> {
        return assets.open("products.json").bufferedReader().use { reader ->
            ProductCatalog.parse(reader.readText())
        }
    }

    private fun openNative(id: String?): Boolean {
        val product = products.firstOrNull { it.id == id } ?: return false
        if (!NativeApps.launch(this, product)) return false
        finish()
        return true
    }
}

internal object NativeApps {
    fun isInstalled(context: Context, product: Product): Boolean {
        val manager = context.packageManager
        return product.packages.any { manager.getLaunchIntentForPackage(it) != null }
    }

    fun launch(context: Context, product: Product): Boolean {
        val manager = context.packageManager
        for (packageName in product.packages) {
            val intent = manager.getLaunchIntentForPackage(packageName) ?: continue
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                continue
            }
        }
        return false
    }
}

@Composable
private fun SuiteScreen(
    edition: String,
    products: List<Product>,
    productId: String?,
    onOpen: (Product) -> Unit,
    onBack: () -> Unit,
) {
    val product = products.firstOrNull { it.id == productId }
    when {
        productId == null -> Directory(edition, products, onOpen)
        product == null -> Missing(edition, onBack)
        product.url == null -> ComingSoon(edition, product, onBack)
        else -> WebShell(product, onBack)
    }
}

@Composable
private fun Directory(edition: String, products: List<Product>, onOpen: (Product) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Wordmark(edition = edition, kicker = "Suite")
            Text(
                "An installed app opens on its own. Otherwise this shell opens the product site, or Coming soon when no site is published. No tracker is added.",
                style = MaterialTheme.typography.bodyMedium,
                color = ThroneMuted,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
        }
        item(key = "nether") {
            val context = LocalContext.current
            ThroneCard {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    HydratedTile(
                        productId = "nether",
                        contentDescription = "Nether",
                        modifier = Modifier.size(52.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text("Nether", style = MaterialTheme.typography.bodyLarge)
                        Text("Passwords, codes, and notes. Same vault as the Nether app.", style = MaterialTheme.typography.bodySmall, color = ThroneMuted)
                    }
                }
                ThroneButton(
                    text = "Open",
                    onClick = { context.startActivity(Intent(context, NetherActivity::class.java)) },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        items(products, key = { it.id }) { product ->
            val context = LocalContext.current
            val kind = when {
                NativeApps.isInstalled(context, product) -> ShelfKind.NATIVE
                product.url != null -> ShelfKind.WEB
                else -> ShelfKind.PLACEHOLDER
            }
            ThroneCard {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    HydratedTile(
                        productId = product.id,
                        contentDescription = product.name,
                        modifier = Modifier.size(52.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(product.name, style = MaterialTheme.typography.bodyLarge)
                        Text(product.summary, style = MaterialTheme.typography.bodySmall, color = ThroneMuted)
                    }
                }
                ThroneButton(
                    text = when (kind) {
                        ShelfKind.NATIVE -> "Open app"
                        ShelfKind.WEB -> "Open site"
                        ShelfKind.PLACEHOLDER -> "Coming soon"
                    },
                    onClick = { onOpen(product) },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun ComingSoon(edition: String, product: Product, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Wordmark(edition = edition, kicker = product.name)
        Text("Coming soon", style = MaterialTheme.typography.headlineSmall)
        Text(product.summary, style = MaterialTheme.typography.bodyMedium)
        ThroneButton(text = "Back", onClick = onBack)
    }
}

@Composable
private fun Missing(edition: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Wordmark(edition = edition, kicker = "Suite")
        Text("That product is not in this edition.", style = MaterialTheme.typography.bodyLarge)
        ThroneButton(text = "Back", onClick = onBack)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebShell(product: Product, onBack: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(product.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            ThroneButton(text = "Close", onClick = onBack)
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    settings.allowUniversalAccessFromFileURLs = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(true)
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.safeBrowsingEnabled = false
                    WebView.setWebContentsDebuggingEnabled(false)
                    val cookies = CookieManager.getInstance()
                    cookies.setAcceptCookie(true)
                    cookies.setAcceptThirdPartyCookies(this, false)
                    webChromeClient = lockedChrome(product, this)
                    webViewClient = lockedClient(product)
                    loadUrl(product.url!!)
                }
            },
            onRelease = { it.destroy() },
        )
    }
}

private fun lockedClient(product: Product) = object : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        return !ProductCatalog.allowsNavigation(product, request.url.toString())
    }

    @Suppress("DEPRECATION")
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
    }
}

private fun lockedChrome(product: Product, host: WebView) = object : WebChromeClient() {
    override fun onPermissionRequest(request: PermissionRequest) {
        request.deny()
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
        callback?.invoke(origin, false, false)
    }

    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
        if (!isUserGesture) return false
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        val popup = WebView(view.context)
        popup.settings.javaScriptEnabled = false
        popup.settings.allowFileAccess = false
        popup.settings.allowContentAccess = false
        popup.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(popupView: WebView, request: WebResourceRequest): Boolean {
                val target = request.url?.toString().orEmpty()
                if (ProductCatalog.allowsNavigation(product, target)) host.loadUrl(target)
                popupView.destroy()
                return true
            }
        }
        transport.webView = popup
        resultMsg.sendToTarget()
        return true
    }
}
