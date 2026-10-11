import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    jacoco
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
        versionCode = 155
        versionName = "1.5.5"

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
            // The unit tests are the debug variant's, so this is where they
            // are measured. See the coverage gate at the end of this file.
            enableUnitTestCoverage = true
        }
    }

    testCoverage {
        jacocoVersion = libs.versions.jacoco.get()
    }

    testOptions {
        unitTests {
            // Robolectric reads the app's own resources: strings, themes and
            // the manifest the screens are drawn against.
            isIncludeAndroidResources = true
            all { test ->
                // The Compose and Robolectric tests are heavy on the heap.
                test.maxHeapSize = "2g"
                // Robolectric loads its native runtime through System.load.
                test.jvmArgs("--enable-native-access=ALL-UNNAMED")
                // Robolectric loads the app's classes through a class loader
                // of its own, which JaCoCo skips unless told otherwise.
                test.extensions.configure<JacocoTaskExtension> {
                    isIncludeNoLocationClasses = true
                    excludes = listOf("jdk.internal.*")
                }
            }
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
    implementation(libs.androidx.compose.material.icons)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    debugImplementation(libs.okhttp.logging)
    implementation(libs.okhttp.sse)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // The frosted glass under the floating navigation bar.
    implementation(libs.haze)
    implementation(libs.haze.blur)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.okhttp.mockwebserver)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability.conf"))
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

// The coverage gate: every line and every branch the unit tests can reach is
// reached, the same bar the add-on holds itself to. `check` runs it, and so
// does CI after the unit tests. What is left out is what nobody wrote: the
// generated BuildConfig and resource classes.
val coverageExclusions = listOf(
    "**/BuildConfig.*",
    "**/R.class",
    "**/R$*.class",
    "**/Manifest*.*",
)

val coverageClasses = files(
    fileTree(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")) {
        exclude(coverageExclusions)
    },
)

val coverageData = layout.buildDirectory.file(
    "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"
)

val jacocoDebugUnitTestReport by tasks.registering(JacocoReport::class) {
    group = "verification"
    description = "Reports the coverage of the debug unit tests."
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(coverageClasses)
    sourceDirectories.setFrom(files("src/main/java", "src/debug/java"))
    executionData.setFrom(coverageData)
    reports {
        xml.required = true
        html.required = true
    }
}

val jacocoDebugUnitTestCoverageVerification by tasks.registering(JacocoCoverageVerification::class) {
    group = "verification"
    description = "Fails the build below full line and branch coverage."
    dependsOn(jacocoDebugUnitTestReport)
    classDirectories.setFrom(coverageClasses)
    sourceDirectories.setFrom(files("src/main/java", "src/debug/java"))
    executionData.setFrom(coverageData)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "1.0".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "1.0".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(jacocoDebugUnitTestCoverageVerification)
}
