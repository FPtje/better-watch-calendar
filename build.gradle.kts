import com.android.build.api.dsl.ApplicationExtension
import dev.detekt.gradle.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.detekt) apply false
}

val jvmTargetVersion: String = libs.versions.jvmTarget.get()
val jvmToolchainVersion: Int = libs.versions.jvmToolchain.get().toInt()

// Settings every module shares, so the module scripts only list what is specific to them.
subprojects {
  pluginManager.withPlugin("dev.detekt") {
    extensions.configure<DetektExtension> {
      buildUponDefaultConfig = true
      config.setFrom(rootProject.file("config/detekt.yml"))
    }
  }

  tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions { this.jvmTarget = JvmTarget.fromTarget(jvmTargetVersion) }
  }

  pluginManager.withPlugin("org.jetbrains.kotlin.plugin.serialization") {
    tasks.withType<KotlinJvmCompile>().configureEach {
      // ProtoBuf and @ProtoNumber are still marked experimental.
      compilerOptions { optIn.add("kotlinx.serialization.ExperimentalSerializationApi") }
    }
  }

  pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    extensions.configure<KotlinBaseExtension> { jvmToolchain(jvmToolchainVersion) }
    extensions.configure<JavaPluginExtension> {
      targetCompatibility = JavaVersion.toVersion(jvmTargetVersion)
    }
  }

  pluginManager.withPlugin("com.android.application") {
    extensions.configure<KotlinBaseExtension> { jvmToolchain(jvmToolchainVersion) }
    extensions.configure<ApplicationExtension> {
      compileSdk = libs.versions.compileSdk.get().toInt()
      buildToolsVersion = libs.versions.buildTools.get()

      defaultConfig {
        // Both apps share one id; the Data Layer only connects apps with the same id and key.
        applicationId = "io.github.fptje.betterwatchcalendar"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
      }

      signingConfigs {
        named("debug") {
          // Shared project key (SPEC §7.4); falls back to the per-user debug key when unset.
          System.getenv("BWC_DEBUG_KEYSTORE")?.let { storeFile = file(it) }
        }
      }

      buildTypes {
        // Debuggable builds run Compose several times slower (ART skips most JIT optimization),
        // so judge UI performance on this one. Signed with the debug key so the phone and watch
        // builds keep pairing.
        named("release") {
          isMinifyEnabled = true
          isShrinkResources = true
          proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
          signingConfig = signingConfigs.getByName("debug")
        }
      }

      compileOptions {
        sourceCompatibility = JavaVersion.toVersion(jvmTargetVersion)
        targetCompatibility = JavaVersion.toVersion(jvmTargetVersion)
      }

      buildFeatures { compose = true }
    }
  }
}

// ktfmt comes from the Nix shell; these tasks only wrap it so `./gradlew` and Studio can run it.
val ktfmtSources =
  fileTree(rootDir) {
    include("**/*.kt", "**/*.kts")
    exclude("**/build/**", ".gradle/**", ".direnv/**", ".kotlin/**")
  }

fun Exec.ktfmt(vararg flags: String) {
  group = "formatting"
  inputs.files(ktfmtSources)
  executable = "ktfmt"
  args("--google-style", *flags)
  args(ktfmtSources.files.map { it.path })
}

tasks.register<Exec>("ktfmtFormat") {
  description = "Formats all Kotlin sources with ktfmt (Google style)."
  ktfmt()
}

tasks.register<Exec>("ktfmtCheck") {
  description = "Fails if any Kotlin source is not ktfmt-formatted."
  ktfmt("--dry-run", "--set-exit-if-changed")
}
