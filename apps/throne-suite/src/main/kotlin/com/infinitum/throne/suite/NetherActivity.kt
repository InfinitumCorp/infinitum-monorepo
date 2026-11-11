package com.infinitum.throne.suite

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import app.infinitum.nether.ui.NetherRoot
import app.infinitum.nether.ui.SharedVault
import app.infinitum.nether.ui.bindNetherLock
import com.infinitum.throne.ui.ThroneTheme

/** Same vault as the standalone Nether app, through the signature-only provider when Nether is installed. */
class NetherActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
            navigationBarStyle = SystemBarStyle.light(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
        )
        bindNetherLock()
        val store = SharedVault.forSuite(this)
        setContent {
            ThroneTheme {
                NetherRoot(store)
            }
        }
    }
}
