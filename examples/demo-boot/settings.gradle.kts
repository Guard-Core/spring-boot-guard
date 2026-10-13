pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
  }
}

rootProject.name = "demo-boot"

// The adapter and the engine are consumed from source until both publish:
// the adapter repo sits two levels up and the engine at the pinned ref
// 9067b7b sits next to it (the same includeBuild convention as the adapter).
includeBuild("../../") {
  dependencySubstitution {
    substitute(module("com.guardcore:spring-boot-guard")).using(project(":"))
  }
}

val engineDir: File = run {
  val candidate: String? =
      providers.gradleProperty("guardCoreJvmDir").orNull
          ?: providers.systemProperty("guardCoreJvmDir").orNull
          ?: System.getenv("GUARD_CORE_JVM_DIR")
  val roots =
      listOfNotNull(
          candidate?.let { rootDir.resolve(it) },
          rootDir.resolve("../../guard-core-jvm"),
          rootDir.resolve("guard-core-jvm"),
      )
  roots.firstOrNull { it.resolve("settings.gradle.kts").isFile }
      ?: throw GradleException(
          "Clone Guard-Core/guard-core-jvm at the pinned ref 9067b7b next to the " +
              "spring-boot-guard repository first (or point -PguardCoreJvmDir / " +
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
