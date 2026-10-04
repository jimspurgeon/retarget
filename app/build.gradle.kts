import org.gradle.api.tasks.Internal

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.retarget.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.retarget.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}

ktlint {
    version.set("1.4.0")
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    source.setFrom(files("src/main/kotlin", "src/test/kotlin"))
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    // detekt validates sources against a JVM target; JBR 25 isn't recognized by its
    // embedded compiler, so pin analysis to the project's target (17).
    jvmTarget = "17"
}

/**
 * Creative-pack license gate (issue #4). Fails the build if any bundled image
 * lacks photographer attribution, license URL, or a sha256, if Unsplash IDs
 * repeat across packs, or if a manifest references files missing from disk.
 * Mirrors scripts/fetch_creatives.py validate_packs(). Runs in `check`.
 *
 * Abstract task + DirectoryProperty is the configuration-cache-compatible
 * pattern: no Gradle script object references at execution time.
 */
abstract class CheckCreativeLicensesTask : DefaultTask() {
    @get:Internal
    abstract val packsDir: DirectoryProperty

    @TaskAction
    fun run() {
        val dir = packsDir.orNull?.asFile
        if (dir == null || !dir.isDirectory) {
            logger.lifecycle("checkCreativeLicenses: no creative packs bundled yet — skipping")
            return
        }
        val seenIds = mutableSetOf<String>()
        val errors = mutableListOf<String>()
        dir.listFiles { f -> f.isDirectory }?.sortedBy { it.name }?.forEach { pack ->
            val manifestFile = pack.resolve("manifest.json")
            if (!manifestFile.isFile) {
                errors.add("${pack.name}: manifest.json missing")
                return@forEach
            }
            val manifest = groovy.json.JsonSlurper().parse(manifestFile) as Map<*, *>

            @Suppress("UNCHECKED_CAST")
            val images = (manifest["images"] as? List<Map<String, Any?>>) ?: emptyList()
            for (img in images) {
                val id = img["unsplashId"]?.toString() ?: ""
                listOf("unsplashId", "file", "photographer", "license", "licenseUrl", "sha256").forEach { field ->
                    if (img[field].toString().isBlank()) errors.add("${pack.name}/$id: missing '$field'")
                }
                if (!seenIds.add(id)) errors.add("${pack.name}/$id: duplicate Unsplash ID across packs")
                if (!pack.resolve(img["file"]?.toString() ?: "").isFile) {
                    errors.add("${pack.name}/$id: file missing on disk")
                }
            }
        }
        if (errors.isNotEmpty()) {
            errors.forEach { logger.error(it) }
            throw GradleException("checkCreativeLicenses: ${errors.size} violation(s)")
        }
        logger.lifecycle("checkCreativeLicenses: all packs valid")
    }
}

tasks.register<CheckCreativeLicensesTask>("checkCreativeLicenses") {
    group = "verification"
    description = "Validate creative-pack manifests for license/attribution integrity."
    packsDir.set(layout.projectDirectory.dir("src/main/assets/creative-packs"))
}

tasks.named("check") {
    dependsOn("checkCreativeLicenses")
}
