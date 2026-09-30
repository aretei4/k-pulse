package com.kahga.kpulse.field

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.kahga.kpulse.field.databinding.ActivityMainBinding

/**
 * The whole app. Everything an agent sees is the React `/agent` route rendered
 * in this WebView — no screens, no API calls, no rules live on the device.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.webView.apply {
            settings.javaScriptEnabled = true
            // The SPA keeps its session token in localStorage; domStorage covers
            // that. (databaseEnabled is the removed WebSQL API — deprecated no-op.)
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportZoom(false)
            settings.mediaPlaybackRequiresUserGesture = false
            overScrollMode = View.OVER_SCROLL_NEVER
            webViewClient = AgentWebViewClient()
        }

        binding.retryButton.setOnClickListener { load() }

        binding.swipeRefresh.setOnRefreshListener {
            binding.webView.reload()
        }

        // In-app history first; only leave the app once the WebView is at its root.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (savedInstanceState == null) {
            load()
        } else {
            binding.webView.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }

    private fun load() {
        binding.errorView.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
        binding.webView.loadUrl(BuildConfig.AGENT_URL)
    }

    private inner class AgentWebViewClient : WebViewClient() {

        /**
         * Keep our own origin inside the WebView. Anything else — a tel: link, a
         * help page — goes to the system browser rather than trapping the agent.
         * Host-level, not path-level: the agent must stay inside /kpulse/ but
         * links within the same host are the app's own navigation.
         */
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val host = request.url.host ?: return false
            val ourHost = android.net.Uri.parse(BuildConfig.AGENT_URL).host
            if (host == ourHost) return false
            return try {
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, request.url))
                true
            } catch (_: android.content.ActivityNotFoundException) {
                true
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            binding.swipeRefresh.isRefreshing = false
            binding.progressBar.visibility = View.GONE
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            // Only the main document failing is worth a full-screen error.
            if (!request.isForMainFrame) return
            binding.swipeRefresh.isRefreshing = false
            binding.progressBar.visibility = View.GONE
            binding.webView.visibility = View.GONE
            binding.errorView.visibility = View.VISIBLE
        }
    }
}
