rootProject.name = "spring-boot-guard"

pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

// ---------------------------------------------------------------------------
// Engine dependency (the Rust path+version pattern, Gradle idiom).
//
// GUARD_CORE_USE_COMPOSITE (environment variable):
//   unset / "1" / "true"  -> composite ON (default). The engine is built from
//     source via includeBuild + dependency substitution: CI (and local builds)
//     check out Guard-Core/guard-core-jvm at the PINNED REF 7711606 as the
//     sibling directory ./guard-core-jvm, and the block below substitutes the
//     declared coordinates com.guard-core:guard-core /
//     com.guard-core:guard-core-engine with the included build's projects.
//   "0" / "false"         -> composite OFF. No includeBuild: the declared
//     coordinates resolve from the repositories (mavenLocal first, then Maven
//     Central). The release workflow runs this way so the published artifact
//     graph is proven against the real engine artifact, not the source
//     checkout; on a release runner mavenLocal is empty and resolution falls
//     through to Maven Central. mavenLocal sits first only so the W8b
//     rehearsal can stand in for Central with publishToMavenLocal output.
//
// The substitution ends when the engine artifacts hit Maven Central (the
// release choreography in docs/release.md): release builds run composite-OFF.
//
// The engine directory (composite ON only) is resolved, in order:
//   1. -PguardCoreJvmDir=<path> (Gradle property)
//   2. GUARD_CORE_JVM_DIR environment variable
//   3. ./guard-core-jvm next to this root (the CI checkout path)
//   4. ../guard-core-jvm (the local sibling-clone convention)
// ---------------------------------------------------------------------------
val compositeRaw: String? = System.getenv("GUARD_CORE_USE_COMPOSITE")
val useComposite: Boolean =
    compositeRaw == null ||
        (compositeRaw != "0" && !compositeRaw.equals("false", ignoreCase = true))

dependencyResolutionManagement {
  repositories {
    if (!useComposite) {
      mavenLocal()
    }
    mavenCentral()
  }
}

if (useComposite) {
  val engineDir: File = run {
    val candidate: String? =
        providers.gradleProperty("guardCoreJvmDir").orNull
            ?: providers.systemProperty("guardCoreJvmDir").orNull
            ?: System.getenv("GUARD_CORE_JVM_DIR")
    val roots =
        listOfNotNull(
            candidate?.let { rootDir.resolve(it) },
            rootDir.resolve("guard-core-jvm"),
            rootDir.parentFile?.resolve("guard-core-jvm"),
        )
    roots.firstOrNull { it.resolve("settings.gradle.kts").isFile }
        ?: throw GradleException(
            "The guard-core-jvm engine checkout was not found. Clone Guard-Core/guard-core-jvm " +
                "at the pinned ref 7711606 next to this repository (or point -PguardCoreJvmDir / " +
                "GUARD_CORE_JVM_DIR at an existing checkout). Searched: " +
                roots.joinToString(", ") { it.path },
        )
  }

  includeBuild(engineDir) {
    dependencySubstitution {
      substitute(module("com.guard-core:guard-core")).using(project(":guard-core"))
      substitute(module("com.guard-core:guard-core-engine")).using(project(":guard-core-engine"))
    }
  }
} else {
  logger.lifecycle(
      "GUARD_CORE_USE_COMPOSITE is disabled: resolving com.guard-core:guard-core from the " +
          "repositories (mavenLocal, then Maven Central).",
  )
}
