import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.detekt)
}

android {
  namespace = "dev.falcop.betterwatchcalendar.wear"
  compileSdk = libs.versions.compileSdk.get().toInt()
  buildToolsVersion = libs.versions.buildTools.get()

  defaultConfig {
    // Must equal :mobile's applicationId; the Data Layer only connects apps with the same id and
    // key.
    applicationId = "dev.falcop.betterwatchcalendar"
    minSdk = libs.versions.minSdk.get().toInt()
    targetSdk = libs.versions.targetSdk.get().toInt()
    versionCode = 1
    versionName = "0.1"
  }

  signingConfigs {
    named("debug") {
      // Shared project key (SPEC §7.4); falls back to the per-user debug key when unset.
      System.getenv("BWC_DEBUG_KEYSTORE")?.let { storeFile = file(it) }
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
  }

  buildFeatures { compose = true }
}

kotlin {
  jvmToolchain(libs.versions.jvmToolchain.get().toInt())
  compilerOptions { jvmTarget = JvmTarget.fromTarget(libs.versions.jvmTarget.get()) }
}

dependencies {
  implementation(project(":shared"))

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(platform(libs.compose.bom))
  implementation(libs.wear.compose.material3)
  implementation(libs.wear.compose.foundation)
  implementation(libs.compose.ui.tooling.preview)
  debugImplementation(libs.compose.ui.tooling)

  implementation(libs.play.services.wearable)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.play.services)
}

detekt {
  buildUponDefaultConfig = true
  config.setFrom(rootProject.file("config/detekt.yml"))
}
