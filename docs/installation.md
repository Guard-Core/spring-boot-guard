# Installation

## Requirements

- JDK 17 or newer (Spring Boot 3.x baseline)
- Spring Boot 3.x (built and tested against 3.5.16; the 4.x lane joins next)
- Redis 6+ for the distributed features (rate limiting, IP bans, behavioral
  state) - `guard.redis-url`, default `redis://localhost:6379`

## Coordinates

Published on Maven Central:

```kotlin
dependencies {
    implementation("com.guard-core:spring-boot-guard:1.2.0")
}
```

The group ID is `com.guard-core` (the Central Portal namespace); Kotlin
packages stay `com.guardcore.*`.

## Kotlin and coroutines versions under Spring Boot dependency management

Spring Boot 3.5 manages `kotlin.version` 1.9.25, `kotlin-coroutines.version` 1.8.1 and `kotlin-serialization.version` 1.6.3, and dependency management overrides the versions this library was built with (Kotlin 2.4.21, coroutines 1.11.0). A Java application that imports the `spring-boot-dependencies` BOM, or uses the Spring Boot Gradle plugin with `io.spring.dependency-management`, therefore fails at startup with `NoSuchMethodError: kotlinx.coroutines.BuildersKt.runBlockingK$default` (coroutines 1.11.0 added `runBlockingK`), and with coroutines 1.11.0 on a Kotlin 1.9 stdlib every request fails with `ClassNotFoundException: kotlin.coroutines.jvm.internal.SpillingKt`. Align both versions. Serialization can stay on the managed 1.6.3.

Gradle with the Spring Boot plugin and `io.spring.dependency-management`:

```groovy
ext['kotlin.version'] = '2.4.21'
ext['kotlin-coroutines.version'] = '1.11.0'
```

Maven with the BOM imported (declare these imports before `spring-boot-dependencies`, the first declaration wins):

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-bom</artifactId>
      <version>2.4.21</version><type>pom</type><scope>import</scope>
    </dependency>
    <dependency>
      <groupId>org.jetbrains.kotlinx</groupId><artifactId>kotlinx-coroutines-bom</artifactId>
      <version>1.11.0</version><type>pom</type><scope>import</scope>
    </dependency>
    <!-- then the spring-boot-dependencies import -->
  </dependencies>
</dependencyManagement>
```

With `spring-boot-starter-parent` as the parent, set `<kotlin.version>2.4.21</kotlin.version>` and `<kotlin-coroutines.version>1.11.0</kotlin-coroutines.version>` as properties instead.

## Enabling and disabling

The guard is on by default as soon as the jar is on the classpath. Set `guard.enabled=false` to switch the whole auto-configuration off: no guard bean, filter or `guard.*` properties binding is created. An app that wants the guard opt-in sets `guard.enabled=false` as its default and turns it on per environment with `guard.enabled=true`.

## Engine dependency (includeBuild + dependency substitution)

Consumers resolve the engine artifact `com.guard-core:guard-core` from Maven Central. This repository's own build pins the engine from source instead: CI and
local builds check out `Guard-Core/guard-core-jvm` at the pinned ref `7711606`
as the sibling directory `./guard-core-jvm`, and `settings.gradle.kts`
substitutes the declared coordinates `com.guard-core:guard-core:4.4.0` /
`com.guard-core:guard-core-engine:4.4.0` with the included build's projects.
The declared dependency coordinates stay `com.guard-core:guard-core:4.4.0`
(lockstep version per the family versioning law).

```bash
git clone https://github.com/Guard-Core/guard-core-jvm.git guard-core-jvm
git -C guard-core-jvm checkout 7711606
./gradlew build
```

The engine directory resolves in order: `-PguardCoreJvmDir=<path>`,
`GUARD_CORE_JVM_DIR`, `./guard-core-jvm`, `../guard-core-jvm`.

Set `GUARD_CORE_USE_COMPOSITE=false` to build without the composite: the
declared coordinates then resolve from the repositories (mavenLocal first,
then Maven Central) - this is how the release workflow proves the published
artifact graph.

## Dependency locking

`gradle.lockfile` is committed and enforced on every resolution: a dependency
change without `./gradlew --write-locks` fails the build. CI additionally
scans the lockfile with osv-scanner (fail on any known vulnerability).
