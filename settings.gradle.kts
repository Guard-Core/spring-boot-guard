rootProject.name = "spring-boot-guard"

// ---------------------------------------------------------------------------
// Engine dependency (the Rust path+version pattern, Gradle idiom).
//
// The engine artifact com.guardcore:guard-core is not on Maven Central until
// the W8 release. Until then the engine is built from source via a composite
// build: CI (and local builds) check out Guard-Core/guard-core-jvm at the
// PINNED REF 5f4ea56 as the sibling directory ./guard-core-jvm, and this
// block substitutes the declared coordinates com.guardcore:guard-core /
// com.guardcore:guard-core-engine with the included build's projects. The
// declared dependency coordinates stay com.guardcore:guard-core:4.3.2
// (lockstep version per the family versioning law) - consumers of a published
// 1.0.0 artifact resolve the real Maven artifact with no composite involved.
//
// The engine directory is resolved, in order:
//   1. -PguardCoreJvmDir=<path> (Gradle property)
//   2. GUARD_CORE_JVM_DIR environment variable
//   3. ./guard-core-jvm next to this root (the CI checkout path)
//   4. ../guard-core-jvm (the local sibling-clone convention)
// ---------------------------------------------------------------------------
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
              "at the pinned ref 5f4ea56 next to this repository (or point -PguardCoreJvmDir / " +
              "GUARD_CORE_JVM_DIR at an existing checkout). Searched: " +
              roots.joinToString(", ") { it.path },
      )
}

includeBuild(engineDir) {
  dependencySubstitution {
    substitute(module("com.guardcore:guard-core")).using(project(":guard-core"))
    substitute(module("com.guardcore:guard-core-engine")).using(project(":guard-core-engine"))
  }
}
