plugins {
    id("com.android.application")
}

// Release signing comes from the environment, so the key never lives in the
// repository. Without it the release build is left unsigned.
val keystorePath = providers.environmentVariable("SBM_KEYSTORE_FILE").orNull
val keystorePassword = providers.environmentVariable("SBM_KEYSTORE_PASSWORD").orNull
val signingReady = !keystorePath.isNullOrBlank() && !keystorePassword.isNullOrBlank() &&
    file(keystorePath).isFile

// Google Play does not allow links to payments outside Google Play, so the
// Play build has no Ko-fi link: ./gradlew bundleRelease -PplayStore=true
val playStore = providers.gradleProperty("playStore").map { it.toBoolean() }.getOrElse(false)

android {
    namespace = "com.equwal.sbm"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.equwal.sbm"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("boolean", "DONATE", (!playStore).toString())
    }

    buildFeatures { buildConfig = true }

    if (signingReady) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = "sbm"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (signingReady) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Google encrypts this block into the APK. F-Droid does not accept it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }

    lint {
        // These checks report newer versions of tools and platforms. Their
        // result changes with the date, not with the code: upgrades are
        // separate work. Google Play asks for target API 36 until August 2027.
        disable += setOf("OldTargetApi", "GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
        warningsAsErrors = true
    }
}

dependencies {
    // Property tests for the file format and the search.
    testImplementation("net.jqwik:jqwik:1.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.4")
}
