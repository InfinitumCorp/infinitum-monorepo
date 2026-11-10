package com.infinitum.throne.suite

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.infinitum.throne.catalog.CatalogStore
import com.infinitum.throne.core.catalog.Product
import com.infinitum.throne.core.catalog.ProductCatalog
import com.infinitum.throne.core.home.EditionPolicy
import com.infinitum.throne.ui.ThroneButton
import com.infinitum.throne.ui.ThroneCard
import com.infinitum.throne.ui.ThroneMuted
import com.infinitum.throne.ui.ThroneTheme
import com.infinitum.throne.ui.Wordmark

class ShellActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
            navigationBarStyle = SystemBarStyle.light(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
        )
        val edition = EditionPolicy.parse(BuildConfig.EDITION)
        val products = CatalogStore.load(this).let { ProductCatalog.visible(it, edition) }
        val requested = intent.getStringExtra(EXTRA_PRODUCT_ID)
        setContent {
            ThroneTheme {
                SuiteScreen(
                    edition = edition.label,
                    products = products,
                    initialId = requested,
                    onOpen = { product ->
                        startActivity(
                            Intent(this, ShellActivity::class.java)
                                .putExtra(EXTRA_PRODUCT_ID, product.id),
                        )
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_PRODUCT_ID = "com.infinitum.throne.suite.PRODUCT_ID"
    }
}

@Composable
private fun SuiteScreen(
    edition: String,
    products: List<Product>,
    initialId: String?,
    onOpen: (Product) -> Unit,
) {
    var productId by remember { mutableStateOf(initialId) }
    val product = products.firstOrNull { it.id == productId }
    when {
        productId == null -> Directory(edition, products, onOpen)
        product == null -> Missing(edition) { productId = null }
        product.url == null -> ComingSoon(edition, product) { productId = null }
        else -> WebShell(product) { productId = null }
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
                "Installed apps open on their own. Everything else here is the product site, or a placeholder when no site is published. This shell adds no tracker.",
                style = MaterialTheme.typography.bodyMedium,
                color = ThroneMuted,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
        }
        items(products, key = { it.id }) { product ->
            ThroneCard {
                Text(product.name, style = MaterialTheme.typography.bodyLarge)
                Text(product.summary, style = MaterialTheme.typography.bodySmall, color = ThroneMuted)
                ThroneButton(
                    text = if (product.url == null) "Coming soon" else "Open",
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
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.safeBrowsingEnabled = true
                    WebView.setWebContentsDebuggingEnabled(false)
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            return !ProductCatalog.allowsNavigation(product, request.url.toString())
                        }
                    }
                    loadUrl(product.url!!)
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
