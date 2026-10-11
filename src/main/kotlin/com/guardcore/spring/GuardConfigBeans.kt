package com.guardcore.spring

import com.guardcore.config.SecurityConfig
import com.guardcore.config.SecurityConfigBuilder

/**
 * Programmatic extension point for the seven SecurityConfig fields that cannot arrive through
 * `guard.*` properties (object-typed: geo_ip_handler, cloud_ip_store, custom_request_check,
 * auth_verifier, custom_response_modifier, on_error, on_block) and for any advanced construction.
 * Customizers run after property binding, before the builder validates (the python
 * SecurityConfig(**kwargs) then `config.field = x` idiom).
 */
public fun interface SecurityConfigCustomizer {
  public fun customize(builder: SecurityConfigBuilder)
}

/**
 * The adapter decorator bean (fastapi-guard's `SecurityDecorator(config)` idiom): the facade
 * decorator whose 25 per-route factories back the Guard* annotations and programmatic use.
 */
public class SpringGuardDecorator(config: SecurityConfig) :
    com.guardcore.decorators.SecurityDecorator(config)
