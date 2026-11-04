package dev.niltsiar.tvhome.service.keys

import android.app.Activity
import android.os.Bundle

/**
 * An invisible activity that closes right away. The key service opens it once at boot (stock_home_redirect): whatever was
 * on screen comes back to the front when it closes and sends a window event, so the service can tell the stock home from
 * an HDMI input without reading the screen (#1116).
 */
public class ProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.post(::close)
    }

    @Suppress("DEPRECATION")
    private fun close() {
        finish()
        overridePendingTransition(0, 0)
    }
}
