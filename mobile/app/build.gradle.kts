plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * Firebase is optional at build time. The google-services plugin fails the build
 * outright when its config file is missing, so it is applied only once
 * `app/google-services.json` exists — until then the app compiles, runs, and
 * falls back to the AGENT_URL below. See README.
 */
val firebaseConfigured = file("google-services.json").exists()
if (firebaseConfigured) {
    apply(plugin = "com.google.gms.google-services")

    /**
     * The demo hand-out is deliberately left out of Firebase. Its applicationId
     * carries the `.demo` suffix, which the plugin treats as a different app and
     * refuses to build unless that package is registered too — and steering the
     * demo remotely is something we do not want anyway: it exists to show the
     * mock data baked into its URL.
     *
     * With this task off there is no FirebaseApp in a demo build, so AgentUrl
     * falls back to AGENT_URL exactly as it does when Firebase is absent
     * altogether. To make the demo steerable instead, register
     * `com.kahga.kpulse.field.demo` in the Firebase console, re-download
     * google-services.json, and delete these two lines.
     */
    tasks.matching { it.name == "processDemoGoogleServices" }.configureEach { enabled = false }
}

android {
    namespace = "com.kahga.kpulse.field"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kahga.kpulse.field"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        debug {
            // 10.0.2.2 is the host machine as seen from the Android emulator.
            buildConfigField("String", "AGENT_URL", "\"https://direco.co.in/kpulse/agent\"")
            buildConfigField("String", "REMOTE_CONFIG_URL_KEY", "\"agent_url\"")
        }
        release {
            buildConfigField("String", "AGENT_URL", "\"https://direco.co.in/kpulse/agent\"")
            buildConfigField("String", "REMOTE_CONFIG_URL_KEY", "\"agent_url\"")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }

        /**
         * Hand-out build that runs on the in-browser mock API.
         *
         * Deliberately a separate artifact rather than a toggle inside the real
         * app: a different applicationId so it installs alongside rather than
         * replacing it, a different launcher name, and `?demo=1` baked into the
         * URL. Keeping the flag in the URL (not just sessionStorage) means it
         * survives rotation and process death — WebView.restoreState restores
         * history but not session storage, so a flag set only at runtime would
         * quietly drop back to live data mid-demo.
         *
         * Inherits debug signing so it installs without a release keystore.
         */
        create("demo") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            buildConfigField("String", "AGENT_URL", "\"https://direco.co.in/kpulse/agent?demo=1\"")
            // Its own key: moving the live app must never drag the demo hand-out
            // off the mock data it exists to show.
            buildConfigField("String", "REMOTE_CONFIG_URL_KEY", "\"agent_url_demo\"")
            // Custom build types need to say which variant of the AndroidX
            // dependencies to resolve against.
            matchingFallbacks += listOf("debug", "release")
        }
    }

    testOptions {
        // android.util.Log is a stub in JVM unit tests; let its calls no-op
        // rather than throw, so the URL rules can be tested without a device.
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Remote Config only — no Analytics, so the app still collects nothing.
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-config-ktx")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
}
