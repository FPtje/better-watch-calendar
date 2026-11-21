// Plain Kotlin/JVM: the domain core needs no Android APIs, and its tests run without Robolectric.
plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.detekt)
}

dependencies {
  api(libs.arrow.core)
  api(libs.kotlinx.serialization.protobuf)

  testImplementation(libs.kotest.runner.junit5)
  testImplementation(libs.kotest.assertions.core)
  testImplementation(libs.kotest.property)
}

tasks.test { useJUnitPlatform() }
