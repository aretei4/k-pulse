plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
        }
        release {
            buildConfigField("String", "AGENT_URL", "\"https://direco.co.in/kpulse/agent\"")
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
            // Custom build types need to say which variant of the AndroidX
            // dependencies to resolve against.
            matchingFallbacks += listOf("debug", "release")
        }
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.google.android.material:material:1.12.0")
}
