import org.gradle.jvm.tasks.Jar

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.spotless)
  alias(libs.plugins.detekt)
  jacoco
  `maven-publish`
  signing
}

// Maven coordinates: groupId com.guard-core (the Central Portal namespace;
// it reverses to guard-core.com). Kotlin packages stay com.guardcore.*:
// hyphens are illegal in JVM packages and the groupId/package mismatch is
// standard practice for hyphenated domains.
group = "com.guard-core"

version = "1.2.0"

// Section 14.3 lockfile analog: every configuration resolves against the
// committed gradle.lockfile. A dependency change without
// `./gradlew --write-locks` fails the build, and the lockfile is what the
// osv-scanner dependency-audit job reads.
dependencyLocking {
  lockAllConfigurations()
  // The family's own artifacts stay out of the lock state: CI substitutes the
  // composite engine build, the release publish job resolves the same version
  // from Maven Central, and the catalog pins it exactly either way.
  ignoredDependencies.add("com.guard-core:*")
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  // The engine facade: com.guard-core:guard-core:4.3.3, substituted with the
  // pinned guard-core-jvm includeBuild checkout (settings.gradle.kts).
  api(libs.guard.core)
  implementation(platform(libs.netty.bom))

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

// --- Publishing (Maven Central via the Central Portal; see docs/release.md) --
//
// The release workflow publishes the staging bundle to the Portal Publisher
// API with GUARD_CORE_USE_COMPOSITE=false so the engine dependency resolves
// from the repositories (mavenLocal, then Maven Central), never the composite
// checkout. Locally the rehearsal uses publishToMavenLocal; signing activates
// only when the GPG secrets are present so the local rehearsal needs no key
// material.

val sourcesJar by
    tasks.registering(Jar::class) {
      archiveClassifier = "sources"
      from(sourceSets["main"].allSource)
    }

// The standard javadoc task renders nothing for Kotlin-only sources; the jar
// carries the project README so it is never an empty javadoc jar (the Portal
// validator rejects those). Dokka HTML replaces this when the docs wave lands.
val javadocJar by
    tasks.registering(Jar::class) {
      archiveClassifier = "javadoc"
      from(tasks.named("javadoc"))
      from(rootProject.file("README.md"))
    }

publishing {
  publications {
    create<MavenPublication>("maven") {
      groupId = project.group.toString()
      artifactId = project.name
      version = project.version.toString()

      from(components["java"])
      artifact(sourcesJar)
      artifact(javadocJar)

      pom {
        name = "spring-boot-guard"
        description =
            "Spring Boot adapter for the Guard ecosystem: GuardFilter + auto-configuration " +
                "running the guard-core-jvm security pipeline (the fastapi-guard of the JVM family)."
        url = "https://github.com/Guard-Core/spring-boot-guard"

        licenses {
          license {
            name = "MIT License"
            url = "https://opensource.org/licenses/MIT"
          }
        }

        developers {
          developer {
            id = "rennf93"
            name = "Renzo Franceschini"
            email = "rennf93@guard-core.com"
            organization = "Guard-Core"
            organizationUrl = "https://github.com/Guard-Core"
          }
        }

        scm {
          connection = "scm:git:https://github.com/Guard-Core/spring-boot-guard.git"
          developerConnection = "scm:git:ssh://github.com/Guard-Core/spring-boot-guard.git"
          url = "https://github.com/Guard-Core/spring-boot-guard"
        }
      }
    }
  }

  repositories {
    maven {
      name = "CentralStaging"
      url = uri(layout.buildDirectory.dir("central-staging"))
    }
  }
}

signing {
  val signingKey = System.getenv("GPG_PRIVATE_KEY")
  val signingPassphrase = System.getenv("GPG_PASSPHRASE")
  if (signingKey != null && signingPassphrase != null) {
    useInMemoryPgpKeys(signingKey, signingPassphrase)
    sign(publishing.publications)
  }
}
