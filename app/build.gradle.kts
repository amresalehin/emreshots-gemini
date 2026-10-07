plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

val bundledOcrModelName = "SmolLM2-135M-Instruct-Q2_K.gguf"
val bundledOcrModelUrl = "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q2_K.gguf"
val bundledOcrModelSha256 = "741ad12b64088fedc17c33aacb22e48be1972ef36a39f03666dd68bd15614fb9"
val bundledOcrModelFile = layout.projectDirectory.file("src/main/assets/models/$bundledOcrModelName").asFile

fun sha256(file: java.io.File): String {
  val digest = MessageDigest.getInstance("SHA-256")
  file.inputStream().use { input ->
    val buffer = ByteArray(1024 * 1024)
    var read: Int
    while (input.read(buffer).also { read = it } != -1) digest.update(buffer, 0, read)
  }
  return digest.digest().joinToString("") { "%02x".format(it) }
}

tasks.register("prepareBundledLocalOcrModel") {
  outputs.file(bundledOcrModelFile)
  doLast {
    bundledOcrModelFile.parentFile.mkdirs()
    if (bundledOcrModelFile.isFile && bundledOcrModelFile.length() > 80L * 1024L * 1024L && sha256(bundledOcrModelFile).equals(bundledOcrModelSha256, ignoreCase = true)) {
      logger.lifecycle("Bundled local OCR LLM already present and verified.")
      return@doLast
    }
    val tempFile = java.io.File(bundledOcrModelFile.parentFile, "$bundledOcrModelName.download")
    if (tempFile.exists()) tempFile.delete()
    logger.lifecycle("Downloading $bundledOcrModelName (~88 MB) for offline OCR cleanup…")
    val connection = (URL(bundledOcrModelUrl).openConnection() as HttpURLConnection).apply {
      connectTimeout = 30_000
      readTimeout = 10 * 60_000
      instanceFollowRedirects = true
      requestMethod = "GET"
      setRequestProperty("User-Agent", "EmreShots-Android-Build")
    }
    try {
      check(connection.responseCode in 200..299) { "Failed to download bundled OCR model: HTTP ${connection.responseCode}" }
      connection.inputStream.use { input ->
        FileOutputStream(tempFile).use { output ->
          val buffer = ByteArray(1024 * 1024)
          var read: Int
          while (input.read(buffer).also { read = it } != -1) output.write(buffer, 0, read)
        }
      }
    } finally { connection.disconnect() }
    check(tempFile.isFile && tempFile.length() > 80L * 1024L * 1024L) { "Downloaded OCR model is unexpectedly small." }
    check(sha256(tempFile).equals(bundledOcrModelSha256, ignoreCase = true)) { "Bundled OCR model SHA-256 mismatch." }
    if (bundledOcrModelFile.exists()) bundledOcrModelFile.delete()
    check(tempFile.renameTo(bundledOcrModelFile)) { "Could not install verified OCR model into assets." }
  }
}

tasks.named("preBuild").configure { dependsOn("prepareBundledLocalOcrModel") }

android {
  namespace = "com.amresalehin.emreshots"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.amresalehin.emreshots"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  val releaseKeystorePath = System.getenv("KEYSTORE_PATH")
  val releaseStorePassword = System.getenv("STORE_PASSWORD")
  val releaseKeyAlias = System.getenv("KEY_ALIAS")
  val releaseKeyPassword = System.getenv("KEY_PASSWORD")
  val hasReleaseSigning = !releaseKeystorePath.isNullOrBlank() &&
      !releaseStorePassword.isNullOrBlank() &&
      !releaseKeyPassword.isNullOrBlank() &&
      !releaseKeyAlias.isNullOrBlank() &&
      file(releaseKeystorePath!!).exists()

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = file(releaseKeystorePath!!)
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
  }

  buildTypes {
    debug { }
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (hasReleaseSigning) {
        signingConfig = signingConfigs.getByName("release")
      } else {
        // CI/local smoke builds must still produce an installable APK.
        // Production distribution should provide KEYSTORE_PATH + credentials.
        signingConfig = signingConfigs.getByName("debug")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  ndkVersion = "26.3.11579264"

  buildFeatures {
    compose = true
    buildConfig = true
  }

  defaultConfig {
    ndk { abiFilters += listOf("arm64-v8a") }
    buildConfigField("String", "PERL5_ASSET_VERSION", "\"2\"")
  }

  androidResources {
    noCompress += listOf("gguf", "traineddata")
  }

  packaging {
    jniLibs {
      useLegacyPackaging = true
    }
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.coil.video)
  implementation(libs.androidx.exifinterface)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okhttp)
  implementation(libs.mlkit.text.recognition)
  implementation("cz.adaptech.tesseract4android:tesseract4android:4.7.0")
  implementation("io.github.ljcamargo:llamacpp-kotlin:0.4.0")
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
