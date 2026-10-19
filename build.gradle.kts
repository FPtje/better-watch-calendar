plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.detekt) apply false
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
