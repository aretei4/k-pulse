package com.kahga.kpulse.field

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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

    /**
     * The URL actually showing. Resolved once per launch from Remote Config's
     * cache (falling back to the build's own), then replaced only if a fetch
     * brings back a different, usable one.
     */
    private lateinit var agentUrl: String

    /**
     * Whether Firebase has already been asked about this failure. Once per failed
     * load: if the URL Remote Config hands back fails too, the agent gets the
     * error screen rather than the two addresses taking turns. Tapping Try again
     * clears it and asks once more.
     */
    private var askedFirebase = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        agentUrl = AgentUrl.current(this)

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
            // FR-U13: the voter-list PDF is fetched by the page with the agent's
            // token, so it arrives as a blob the download manager cannot see.
            // The page hands it to this bridge instead.
            addJavascriptInterface(DownloadBridge(this@MainActivity), DownloadBridge.NAME)
            // Anything the page does hand over as a real URL still goes to the
            // system downloader rather than silently doing nothing.
            setDownloadListener { url, _, _, _, _ -> openExternally(android.net.Uri.parse(url)) }
        }

        binding.retryButton.setOnClickListener {
            // A deliberate retry is worth another question to Firebase: the agent
            // may be tapping it an hour later, somewhere with signal.
            askedFirebase = false
            load()
        }

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

        // Asked for after the page is already loading, never before it: a slow or
        // failed fetch must not leave an agent staring at a blank screen.
        AgentUrl.refresh(this, agentUrl) { moved ->
            agentUrl = moved
            load()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }

    /** A link or file this app does not render itself; the phone decides what does. */
    private fun openExternally(uri: android.net.Uri) {
        try {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
        } catch (_: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(this, R.string.download_no_viewer, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun load() {
        binding.errorView.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
        // Shown again for a retry or a rescue, both of which follow a failure that
        // hid it; onPageFinished takes it away.
        binding.progressBar.visibility = View.VISIBLE
        binding.webView.loadUrl(agentUrl)
    }

    /**
     * The URL compiled into the build can simply be wrong by now — a host moved,
     * a domain lapsed, a path renamed — and Remote Config is where the current one
     * lives. Asked for only after a load has already failed, so a working app
     * never waits on it, and at most once per failure.
     *
     * A different, usable URL replaces the one on screen and reloads, which
     * clears the error view by itself. Anything else leaves that view as it is,
     * with its Try again button.
     */
    private fun askFirebaseForTheCurrentUrl() {
        if (askedFirebase) return
        askedFirebase = true
        AgentUrl.rescue(this, agentUrl) { moved ->
            if (moved == null || isFinishing || isDestroyed) return@rescue
            agentUrl = moved
            load()
        }
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
            val ourHost = AgentUrl.hostOf(agentUrl)
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
            // No signal and a dead address look the same from here, so ask either
            // way: with no network the fetch fails too and this view stays put.
            askFirebaseForTheCurrentUrl()
        }

        /**
         * The host answered, but not with the app — a 404 from a renamed path, a
         * 502 from a server that has moved on. The WebView renders whatever came
         * back, so there is nothing to put an error screen over; it is still
         * worth asking whether the address has changed.
         */
        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            response: WebResourceResponse,
        ) {
            if (!request.isForMainFrame || response.statusCode < 400) return
            askFirebaseForTheCurrentUrl()
        }
    }
}
