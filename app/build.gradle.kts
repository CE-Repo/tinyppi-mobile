import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Where the release signing material comes from: the environment first, so CI
// can hand it over as secrets without writing anything into the repository,
// then a gitignored `keystore.properties` at the root for local release
// builds. Read through `providers` and a tracked file read, both of which the
// configuration cache understands -- `System.getenv` would invalidate it.
data class ReleaseSigning(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

val releaseSigning: ReleaseSigning? = run {
    val properties = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.isFile) file.inputStream().use { load(it) }
    }

    fun value(environment: String, property: String): String? =
        providers.environmentVariable(environment).orNull?.takeIf { it.isNotBlank() }
            ?: properties.getProperty(property)?.takeIf { it.isNotBlank() }

    // Resolved against the repository root, not `app/`, so a relative
    // `storeFile` reads the way the `keystore.properties` beside it looks.
    val store = value("KEYSTORE_FILE", "storeFile")?.let(rootProject::file) ?: return@run null
    if (!store.isFile) return@run null

    ReleaseSigning(
        storeFile = store,
        storePassword = value("KEYSTORE_PASSWORD", "storePassword") ?: return@run null,
        keyAlias = value("KEY_ALIAS", "keyAlias") ?: return@run null,
        keyPassword = value("KEY_PASSWORD", "keyPassword") ?: return@run null,
    )
}

android {
    namespace = "com.jamal2367.tinyppimobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jamal2367.tinyppimobile"
        minSdk = 26
        targetSdk = 37
        versionCode = 12
        versionName = "1.2.0"

        // Which run of the release workflow this build came from, or 0 for one
        // built from a checkout by hand. It is what the update check compares
        // against: the workflow tags every build `build-<run number>` and the
        // version name stands still across dozens of them, so the run number is
        // the only thing that tells two releases of `1.2.0` apart. A build that
        // carries 0 knows of no run behind it and is never told it is behind by
        // one -- a newer version name still reaches it.
        val buildNumber = providers.environmentVariable("BUILD_NUMBER").orNull
            ?.trim()
            ?.toIntOrNull()
            ?: 0
        buildConfigField("int", "BUILD_NUMBER", "$buildNumber")
    }

    val signing = releaseSigning

    signingConfigs {
        if (signing != null) {
            create("release") {
                storeFile = signing.storeFile
                storePassword = signing.storePassword
                keyAlias = signing.keyAlias
                keyPassword = signing.keyPassword
            }
        }
    }

    buildTypes {
        release {
            // Falls back to the debug key when no release material is
            // configured, so `assembleRelease` still yields an installable APK
            // on a checkout that has no keystore -- an unsigned APK is one
            // nobody can do anything with.
            signingConfig = signingConfigs.getByName(
                if (signing != null) "release" else "debug"
            )

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.sse)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
