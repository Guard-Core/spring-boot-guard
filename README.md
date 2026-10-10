<p align="center">
    <a href="https://guard-core.github.io/guard-core/latest/">
        <img src="https://guard-core.github.io/guard-core/latest/assets/guard_core_legend.svg" alt="Guard Core">
    </a>
</p>

___

<p align="center">
    <strong>Spring Boot adapter for the [guard-core](https://github.com/Guard-Core/guard-core-jvm) engine for the JVM.</strong>
</p>

<p align="center">
    <a href="https://github.com/Guard-Core/spring-boot-guard/actions/workflows/ci.yml">
        <img src="https://github.com/Guard-Core/spring-boot-guard/actions/workflows/ci.yml/badge.svg" alt="CI">
    </a>
    <a href="https://github.com/Guard-Core/spring-boot-guard/actions/workflows/code-ql.yml">
        <img src="https://github.com/Guard-Core/spring-boot-guard/actions/workflows/code-ql.yml/badge.svg" alt="CodeQL">
    </a>
    <a href="https://opensource.org/licenses/MIT">
        <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License">
    </a>
</p>

___

## Status

W0 scaffold. The repo builds with the Gradle toolchain (JDK 17, Kotlin) and CI is wired, but no adapter functionality exists yet. Spring Boot 3.x is the current lane; the 4.x lane joins the adapter test matrix at W6, together with the `com.guardcore:guard-core:4.3.2` engine dependency once the engine artifact is on Maven Central.

## What lands here

- Auto-configuration wiring the guard-core engine into Boot applications.
- Servlet and reactive request filters feeding the guard-core pipeline.
- Configuration surface mirroring the engine's `SecurityConfig`.

## Install

Not published yet. The first artifact ships with the 1.0.0 release.

## License

[MIT](LICENSE)
