// Android, signing and Kotlin settings shared with :mobile live in the root build script.
plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.detekt)
}

android { namespace = "io.github.fptje.betterwatchcalendar.wear" }

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

  implementation(libs.wear.complications.data.source.ktx)
  implementation(libs.androidx.datastore)

  implementation(libs.play.services.wearable)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.play.services)
}
