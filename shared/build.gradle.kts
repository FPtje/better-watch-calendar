import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Plain Kotlin/JVM: the domain core needs no Android APIs, and its tests run without Robolectric.
plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.detekt)
}

kotlin {
  jvmToolchain(libs.versions.jvmToolchain.get().toInt())
  compilerOptions {
    jvmTarget = JvmTarget.fromTarget(libs.versions.jvmTarget.get())
    // ProtoBuf and @ProtoNumber are still marked experimental.
    optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
  }
}

java { targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get()) }

dependencies {
  api(libs.arrow.core)
  api(libs.kotlinx.serialization.protobuf)

  testImplementation(libs.kotest.runner.junit5)
  testImplementation(libs.kotest.assertions.core)
  testImplementation(libs.kotest.property)
}

tasks.test { useJUnitPlatform() }

detekt {
  buildUponDefaultConfig = true
  config.setFrom(rootProject.file("config/detekt.yml"))
}
