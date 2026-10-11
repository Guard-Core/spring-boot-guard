plugins {
  kotlin("jvm") version "2.4.21"
  kotlin("plugin.spring") version "2.4.21"
  id("org.springframework.boot") version "3.5.16"
}

group = "com.guardcore.examples"

version = "1.0.0-SNAPSHOT"

kotlin {
  jvmToolchain(17)
}

repositories {
  mavenCentral()
}

dependencies {
  implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.16"))
  implementation("com.guardcore:spring-boot-guard:1.0.0-SNAPSHOT")
  implementation("org.springframework.boot:spring-boot-starter-web")
}
