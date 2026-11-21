import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.detekt)
}

android {
  namespace = "io.github.fptje.betterwatchcalendar.mobile"
  compileSdk = libs.versions.compileSdk.get().toInt()
  buildToolsVersion = libs.versions.buildTools.get()

  defaultConfig {
    // Must equal :wear's applicationId; the Data Layer only connects apps with the same id and key.
    applicationId = "io.github.fptje.betterwatchcalendar"
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

  buildTypes {
    // Signed with the debug key to keep pairing with :wear (see its release build type).
    named("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
      signingConfig = signingConfigs.getByName("debug")
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
  compilerOptions {
    jvmTarget = JvmTarget.fromTarget(libs.versions.jvmTarget.get())
    // The debug log is stored as ProtoBuf, which is still marked experimental.
    optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
  }
}

dependencies {
  implementation(project(":shared"))

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(platform(libs.compose.bom))
  implementation(libs.compose.material3)
  implementation(libs.compose.ui.tooling.preview)
  debugImplementation(libs.compose.ui.tooling)

  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.datastore)
  implementation(libs.androidx.datastore.preferences)

  implementation(libs.play.services.wearable)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.play.services)
}

detekt {
  buildUponDefaultConfig = true
  config.setFrom(rootProject.file("config/detekt.yml"))
}
