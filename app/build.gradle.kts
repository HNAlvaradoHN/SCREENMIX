val releaseKeystorePath = System.getenv("SCREENMIX_KEYSTORE_PATH")
val releaseKeystorePassword = System.getenv("SCREENMIX_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("SCREENMIX_KEY_ALIAS")
val releaseKeyPassword = System.getenv("SCREENMIX_KEY_PASSWORD")

val hasReleaseSigning = listOf(
  releaseKeystorePath,
  releaseKeystorePassword,
  releaseKeyAlias,
  releaseKeyPassword,
).all { !it.isNullOrBlank() }

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.screenmix.app"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.screenmix.app"
    minSdk = 26
    targetSdk = 36
    versionCode = 10
    versionName = "0.3.2"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = file(releaseKeystorePath!!)
        storePassword = releaseKeystorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      if (hasReleaseSigning) {
        signingConfig = signingConfigs.getByName("release")
      }
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.savedstate)
  implementation(libs.androidx.lifecycle.service)
  testImplementation(libs.junit)
  debugImplementation(libs.androidx.compose.ui.tooling)
}



tasks.register("verifyReleaseSigning") {
  doLast {
    check(hasReleaseSigning) {
      "Release signing is not configured. Provide SCREENMIX_KEYSTORE_PATH, SCREENMIX_KEYSTORE_PASSWORD, SCREENMIX_KEY_ALIAS, and SCREENMIX_KEY_PASSWORD."
    }
    check(file(releaseKeystorePath!!).isFile) {
      "Release keystore file was not found."
    }
  }
}
