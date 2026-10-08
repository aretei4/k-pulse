package com.kahga.kpulse.field

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import java.net.URI

/**
 * Where the WebView points.
 *
 * Firebase Remote Config first, the value compiled into the build second. That
 * ordering is the point: the URL can be moved — a new host, a maintenance page,
 * a staging cut-over — without shipping an APK and waiting on review.
 *
 * Everything here degrades to [BuildConfig.AGENT_URL]:
 *
 *  * no `google-services.json` in the build, so Firebase never initialised;
 *  * Firebase present but the fetch failed (offline, quota, bad network);
 *  * a value came back that is not a usable https URL.
 *
 * The last case matters most. A typo in the Remote Config console would
 * otherwise brick every installed app at once, with no way to correct it except
 * another console edit that the broken app may never fetch. Refusing anything
 * that is not https with a host means the worst a bad value can do is leave the
 * app on the URL it already had.
 */
object AgentUrl {

    private const val TAG = "AgentUrl"

    /**
     * Debug and demo builds fetch on every launch; a release build keeps
     * Firebase's default throttle so normal use does not hammer the service.
     */
    private val FETCH_INTERVAL_SECONDS = if (BuildConfig.DEBUG) 0L else 3600L

    private var remoteConfig: FirebaseRemoteConfig? = null

    /**
     * The URL to load right now — the last value Remote Config activated, or the
     * build's own. Returns immediately: Remote Config reads from its local cache,
     * so the WebView never waits on the network to show something.
     */
    fun current(context: Context): String {
        val config = config(context)
        if (config == null) {
            Log.i(TAG, "Loading the build's URL (Firebase not configured in this build): ${BuildConfig.AGENT_URL}")
            return BuildConfig.AGENT_URL
        }
        val cached = usable(config.getString(BuildConfig.REMOTE_CONFIG_URL_KEY))
        // Says plainly which source won, so a field diagnosis is one logcat line
        // rather than guesswork — `adb logcat -s AgentUrl`.
        if (cached == null || cached == BuildConfig.AGENT_URL) {
            Log.i(TAG, "Loading the build's URL: ${BuildConfig.AGENT_URL}")
            return BuildConfig.AGENT_URL
        }
        Log.i(TAG, "Loading the Remote Config URL: $cached")
        return cached
    }

    /**
     * Fetches in the background and calls [onChanged] only when the result is a
     * usable URL that differs from [loaded] — so a routine fetch never reloads
     * the page under the agent for nothing.
     */
    fun refresh(context: Context, loaded: String, onChanged: (String) -> Unit) {
        val config = config(context) ?: return
        config.fetchAndActivate()
            .addOnSuccessListener {
                val fetched = usable(config.getString(BuildConfig.REMOTE_CONFIG_URL_KEY))
                if (fetched == null) {
                    Log.i(TAG, "Remote Config has no usable URL; staying on the build's own")
                    return@addOnSuccessListener
                }
                if (fetched == loaded) {
                    Log.i(TAG, "Remote Config fetched; same URL as the one loaded, nothing to do")
                    return@addOnSuccessListener
                }
                Log.i(TAG, "Remote Config moved the agent URL to $fetched")
                onChanged(fetched)
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Remote Config fetch failed; staying on the build's own URL", error)
            }
    }

    /** The host the WebView treats as its own, whichever source the URL came from. */
    fun hostOf(url: String): String? = runCatching { URI(url).host }.getOrNull()

    /**
     * The one rule a remote value has to pass. Plain JVM parsing, so it is unit
     * tested without a device — this is the check standing between a console
     * typo and every installed app pointing somewhere useless.
     */
    internal fun usable(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
        // https only, and a real host: http would downgrade every install at
        // once, and a hostless value would not load at all.
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) {
            Log.w(TAG, "Ignoring a Remote Config URL that is not https with a host")
            return null
        }
        return uri.toString()
    }

    /**
     * Null whenever Firebase is not set up in this build — no `google-services.json`
     * means no FirebaseApp, which is a normal state here, not an error.
     */
    private fun config(context: Context): FirebaseRemoteConfig? {
        remoteConfig?.let { return it }
        return try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context) ?: return null
            }
            FirebaseRemoteConfig.getInstance().also { config ->
                config.setConfigSettingsAsync(
                    FirebaseRemoteConfigSettings.Builder()
                        .setMinimumFetchIntervalInSeconds(FETCH_INTERVAL_SECONDS)
                        .build()
                )
                // No setDefaults call: it is applied asynchronously, so the first
                // read still misses it, and BuildConfig.AGENT_URL is already the
                // fallback everywhere below. One default, not two.
                remoteConfig = config
            }
        } catch (error: Throwable) {
            Log.i(TAG, "Firebase is not configured in this build; using the build's own URL")
            null
        }
    }
}
