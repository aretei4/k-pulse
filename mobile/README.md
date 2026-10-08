# K-Pulse Field — Android shell

A deliberately thin native wrapper. Its only job is to open a WebView on the
`/agent` route of the deployed React app, so agent-side UI changes ship with a
web redeploy instead of an app-store review.

What lives here: the WebView, back-navigation, a no-network screen, and OTP
auto-fill hooks. What does *not*: any screen, any API call, any business rule.

## Configure the URL

`app/build.gradle.kts` sets `AGENT_URL` per build type:

| Build type | URL | Installs as |
|---|---|---|
| `release` | `https://direco.co.in/kpulse/agent` | K-Pulse Field |
| `demo` | `https://direco.co.in/kpulse/agent?demo=1` | K-Pulse Demo |
| `debug` | `http://10.0.2.2:5173/kpulse/agent` — the Vite dev server as seen from an emulator | K-Pulse Field |

### Firebase Remote Config overrides it

At launch the app asks Firebase Remote Config for the URL and falls back to the
`AGENT_URL` above. That way the WebView can be pointed somewhere else — a new
host, a staging cut-over, a maintenance page — without shipping an APK and
waiting on Play review.

| Build type | Remote Config key |
|---|---|
| `release`, `debug` | `agent_url` |
| `demo` | `agent_url_demo` |

The demo build reads its own key on purpose: moving the live app must not drag
the hand-out demo off the mock data it exists to show.

**The app works with no Firebase at all.** The google-services plugin is applied
only when `app/google-services.json` exists, so until you add it the build runs
and every launch uses `AGENT_URL`. To turn Remote Config on:

1. In the Firebase console, create a project and add an Android app for
   **`com.kahga.kpulse.field`** — and a second one for
   **`com.kahga.kpulse.field.demo`** if you want to steer the demo build too.
2. Download `google-services.json` and put it in `mobile/app/`. Keep it out of
   git if the repo is public: it is not a secret, but it identifies the project.
3. Rebuild. The plugin picks itself up automatically.
4. In Remote Config, add the key above with the full `https://…/kpulse/agent`
   URL and publish.

Rules the app applies to whatever comes back:

- **https with a real host, or it is ignored.** A console typo, an `http://`
  value or a `javascript:` string leaves the app on the URL it already had —
  otherwise one bad edit would brick every install at once, including the apps
  that would need a later edit to recover.
- **The page loads first, then the fetch happens.** The WebView starts on the
  last known good URL (Remote Config's cache, else the build's), so a slow or
  failed fetch never leaves an agent looking at a blank screen. The page is
  reloaded only if the fetch actually returns a *different* usable URL.
- **Release builds use Firebase's hourly throttle**; debug and demo builds fetch
  on every launch, so a change shows up immediately while you are testing.

The URL rules are unit tested in `app/src/test/.../AgentUrlTest.kt`
(`./gradlew :app:testDebugUnitTest`).

## The demo build

```bash
./gradlew assembleDemo   # -> app/build/outputs/apk/demo/app-demo.apk
```

A hand-out build that runs entirely on the SPA's in-browser mock API — no
Spring Boot backend needed. It is a **separate app**, not a toggle:

- `applicationId` is `com.kahga.kpulse.field.demo`, so it installs *alongside*
  the real app instead of replacing it, and is labelled "K-Pulse Demo".
- `?demo=1` is baked into the URL rather than set at runtime. `WebView.saveState`
  restores history but **not** sessionStorage, where the web app's demo flag
  normally lives — a runtime-only flag would silently revert to live data after
  a rotation or process death, mid-demo.
- It signs with the debug keystore, so it installs without a release keystore.

A real agent's install can therefore never be flipped into fake data, which
matters on an app that shows voter PII.

The demo build still loads the SPA from `direco.co.in`, so the web bundle has to
be deployed — it just doesn't need the API to be up.

The `/kpulse` segment is the sub-path the app is mounted on; it has to match
Vite's `base`, the router `basename`, and the nginx `location /kpulse/`.

Change those two lines to point at staging.

## Build

```bash
cd mobile && ./gradlew assembleDebug     # -> app/build/outputs/apk/debug/app-debug.apk
```

## Launcher icon

One continuous stroke that drops into a trough and rises out of it as a tick —
a pulse reading and "recorded" in the same gesture. Marigold `#C98A2E` on ink
`#152A38`, the app's existing tokens.

| Layer | File |
|---|---|
| Adaptive (API 26+) | `mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` |
| Foreground | `drawable/ic_launcher_foreground.xml` |
| Themed, Android 13+ | `drawable/ic_launcher_monochrome.xml` |
| Background colour | `values/ic_launcher_background.xml` |
| Legacy (API 24-25) | `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher*.png` |

The vector path is sized so every point **plus half the stroke width** stays
inside the central 72dp of the 108dp canvas. That is the only region an
adaptive icon is guaranteed to show, whatever mask the launcher applies — the
first draft overflowed it and the tick's tip clipped under a circular mask.

The `demo` build type overrides the foreground and background (paper on
marigold-deep) so the two apps are told apart at a glance in the launcher,
reinforcing that the demo build is a separate app rather than a mode.

Regenerate with `tools/gen_icons.py` if the mark changes; there is no
binary source file to keep in sync — the PNGs are drawn from the same
coordinates as the vector.

## Toolchain — these versions are a set

| | |
|---|---|
| Gradle | **8.9** (`gradle/wrapper/gradle-wrapper.properties`) |
| Android Gradle Plugin | **8.5.2** |
| Kotlin | **1.9.24** |
| JDK | **17** |
| compileSdk / targetSdk | 34, minSdk 24 |

Gradle 9 does **not** work with this AGP/Kotlin pair — it fails during
configuration, so `BuildConfig`, `R` and the view bindings are never generated
and the editor reports every reference to them as unresolved. If you want to
move to Gradle 9, AGP and Kotlin have to go up together (AGP 8.11+, Kotlin 2.x).

If Android Studio offers an AGP upgrade, decline it unless you are making that
whole jump deliberately.

No launcher icon is checked in — add one in Android Studio (`res/mipmap`) and
set `android:icon` on `<application>` before shipping.

## Cleartext traffic

`network_security_config.xml` permits cleartext only to `10.0.2.2` and
`localhost`, so the debug build can reach a local dev server while release
traffic stays HTTPS-only.
