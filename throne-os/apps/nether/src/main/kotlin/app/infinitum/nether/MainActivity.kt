package app.infinitum.nether

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.view.autofill.AutofillManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import app.infinitum.nether.ui.NetherRoot
import app.infinitum.nether.ui.NetherSession
import app.infinitum.nether.ui.SharedVault
import app.infinitum.nether.ui.bindNetherLock
import com.infinitum.throne.ui.ThroneTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
            navigationBarStyle = SystemBarStyle.light(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
        )
        bindNetherLock()
        val store = SharedVault.forNether(this)
        setContent {
            ThroneTheme {
                NetherRoot(store)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (intent?.getBooleanExtra(NetherAutofillService.EXTRA_AUTH, false) == true && NetherSession.unlocked) {
            deliverAutofill()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun deliverAutofill() {
        val structure = NetherAutofillService.pending ?: return
        val response = NetherAutofillService.responseFor(this, structure) ?: return
        val data = Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response)
        setResult(RESULT_OK, data)
        NetherAutofillService.pending = null
        finish()
    }
}
