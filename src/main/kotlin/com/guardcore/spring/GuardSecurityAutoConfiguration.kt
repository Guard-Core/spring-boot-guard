package com.guardcore.spring

import org.springframework.boot.autoconfigure.AutoConfiguration

/**
 * Boot auto-configuration entry point for the Guard Core adapter.
 *
 * W0 scaffold: the class exists so the artifact, toolchain, and CI wiring are exercisable end to
 * end. The engine dependency and the real security configuration land in W6.
 */
@AutoConfiguration class GuardSecurityAutoConfiguration
