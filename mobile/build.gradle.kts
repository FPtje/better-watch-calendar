// Android, signing and Kotlin settings shared with :wear live in the root build script.
plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  // The debug log is stored as ProtoBuf.
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.detekt)
}

android { namespace = "io.github.fptje.betterwatchcalendar.mobile" }

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
