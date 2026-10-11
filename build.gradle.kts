plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.spotless)
  alias(libs.plugins.detekt)
  jacoco
}

group = "com.guardcore"

version = "1.0.0-SNAPSHOT"

kotlin {
  jvmToolchain(17)
}

repositories {
  mavenCentral()
}

dependencies {
  // The engine facade: com.guardcore:guard-core:4.3.2, substituted with the
  // pinned guard-core-jvm includeBuild checkout (settings.gradle.kts).
  api(libs.guard.core)

  // Spring's Kotlin handler introspection (suspend-fun detection) requires kotlin-reflect at
  // runtime whenever a Kotlin handler or the Kotlin adapter classes are exercised.
  api(libs.kotlin.reflect)
  implementation(libs.kotlin.stdlib)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.spring.boot.autoconfigure)

  // Boot BOM: pins the implementation-surface versions for consumers too (the POM carries
  // the managed versions instead of bare coordinates).
  implementation(platform(libs.spring.boot.dependencies))

  // Servlet/Boot web surface: provided by the consuming application's
  // spring-boot-starter-web at runtime; compile-only here (the auto-
  // configuration is @ConditionalOnClass-gated on these classes).
  compileOnly(libs.spring.web)
  compileOnly(libs.spring.webmvc)
  compileOnly(libs.jakarta.servlet.api)

  testImplementation(libs.spring.boot.starter.test)
  testImplementation(libs.spring.boot.starter.web)
  // Gradle 9 requires the platform launcher on the test runtime (boot docs convention).
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
  useJUnitPlatform()
  // Redis-backed tests PING localhost:6379 and skip when it is unreachable
  // (documented in README; CI runs a redis service container).
  testLogging {
    events("passed", "skipped", "failed")
  }
}

spotless {
  kotlin {
    ktfmt()
  }
  kotlinGradle {
    ktfmt()
  }
}

detekt {
  buildUponDefaultConfig = true
  config.setFrom(rootProject.files("detekt.yml"))
}

// JaCoCo reporting on every build; the coverage gate is a later milestone.
