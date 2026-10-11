package com.guardcore.spring

/**
 * The fastapi-guard re-export convention (guard/__init__.py:3-33): the adapter's public API
 * re-exports the engine facade's user-facing types so consumers import from the adapter package and
 * never couple to the engine coordinates directly.
 *
 * Kotlin typealiases keep the re-exports zero-cost; the guard.* property surface and the Guard*
 * annotations are declared in this package.
 */

// config
public typealias SecurityConfig = com.guardcore.config.SecurityConfig

public typealias SecurityConfigBuilder = com.guardcore.config.SecurityConfigBuilder

public typealias BehaviorRuleConfig = com.guardcore.config.BehaviorRuleConfig

public typealias ThreatBanConfig = com.guardcore.config.ThreatBanConfig

// protocols
public typealias GuardRequest = com.guardcore.protocols.GuardRequest

public typealias GuardResponse = com.guardcore.protocols.GuardResponse

public typealias GuardResponseFactory = com.guardcore.protocols.GuardResponseFactory

public typealias GeoIPHandler = com.guardcore.protocols.GeoIPHandler

public typealias RedisHandlerProtocol = com.guardcore.protocols.RedisHandlerProtocol

public typealias RouteAuthVerifier = com.guardcore.decorators.RouteAuthVerifier

// decorators
public typealias SecurityDecorator = com.guardcore.decorators.SecurityDecorator

public typealias BaseSecurityDecorator = com.guardcore.decorators.BaseSecurityDecorator

public typealias RouteConfig = com.guardcore.decorators.RouteConfig

// handlers
public typealias RedisManager = com.guardcore.handlers.RedisManager

public typealias RateLimitManager = com.guardcore.handlers.RateLimitManager

public typealias IpBanManager = com.guardcore.handlers.IpBanManager

public typealias IpInfoManager = com.guardcore.handlers.IpInfoManager

public typealias CloudManager = com.guardcore.handlers.CloudManager

public typealias SecurityHeadersManager = com.guardcore.handlers.SecurityHeadersManager

public typealias BehaviorTracker = com.guardcore.handlers.BehaviorTracker

public typealias BehaviorRule = com.guardcore.handlers.BehaviorRule
