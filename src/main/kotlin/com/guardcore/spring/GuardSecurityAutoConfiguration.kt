package com.guardcore.spring

import com.guardcore.config.SecurityConfig
import com.guardcore.config.SecurityConfigBuilder
import com.guardcore.protocols.AgentHandlerProtocol
import jakarta.servlet.DispatcherType
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.http.converter.json.KotlinSerializationJsonHttpMessageConverter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.servlet.DispatcherServlet
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Boot auto-configuration for the Guard Core adapter (the fastapi-guard wiring idiom:
 * `app.add_middleware(SecurityMiddleware, config=config)`, Spring-idiomatized as beans).
 *
 * Beans: SecurityConfig (guard.* properties + customizers), SpringGuardDecorator, GuardMiddleware
 * (the engine-facing middleware), GuardFilter (registered over every dispatcher type so error
 * dispatches stay guarded too, matching the ASGI middleware's envelope). `guard.eager-init=true`
 * additionally registers the GuardEagerInitializer boot hook.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(HttpServletRequest::class, DispatcherServlet::class)
@EnableConfigurationProperties(GuardProperties::class)
public open class GuardSecurityAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean(SecurityConfig::class)
  public open fun securityConfig(
      properties: GuardProperties,
      customizers: ObjectProvider<SecurityConfigCustomizer>,
  ): SecurityConfig {
    val builder = SecurityConfigBuilder()
    properties.applyTo(builder)
    for (customizer in customizers) {
      customizer.customize(builder)
    }
    return builder.build()
  }

  @Bean
  @ConditionalOnMissingBean(SpringGuardDecorator::class)
  public open fun guardDecorator(config: SecurityConfig): SpringGuardDecorator =
      SpringGuardDecorator(config)

  @Bean
  @ConditionalOnMissingBean(GuardMiddleware::class)
  public open fun guardMiddleware(
      config: SecurityConfig,
      decorator: SpringGuardDecorator,
      agentHandler: ObjectProvider<AgentHandlerProtocol>,
  ): GuardMiddleware =
      GuardMiddleware(
          config,
          decorator = decorator,
          injectedAgentHandler = agentHandler.ifAvailable,
      )

  @Bean
  public open fun guardAnnotationConfigurator(
      decorator: SpringGuardDecorator,
      beanFactory: ConfigurableBeanFactory,
  ): AnnotationRouteConfigurator = AnnotationRouteConfigurator(decorator, beanFactory)

  @Bean
  @ConditionalOnBean(DispatcherServlet::class)
  public open fun guardFilterRegistration(
      middleware: GuardMiddleware,
      decorator: SpringGuardDecorator,
      configurator: AnnotationRouteConfigurator,
      handlerMappings: ObjectProvider<HandlerMapping>,
  ): FilterRegistrationBean<GuardFilter> {
    val filter =
        GuardFilter(
            middleware = middleware,
            handlerMappings = handlerMappings.orderedStream().toList(),
            annotationConfigurator = configurator,
            decoratorHandler = decorator,
        )
    val registration =
        FilterRegistrationBean(filter).apply {
          // Every dispatcher type: REQUEST, ERROR, FORWARD, ASYNC, INCLUDE - the ASGI middleware
          // envelope guards everything the app serves, including Boot's /error dispatches.
          setDispatcherTypes(
              DispatcherType.REQUEST,
              DispatcherType.ERROR,
              DispatcherType.FORWARD,
              DispatcherType.ASYNC,
              DispatcherType.INCLUDE,
          )
          order = Ordered.HIGHEST_PRECEDENCE + 10
        }
    return registration
  }

  @Bean
  @ConditionalOnProperty(prefix = "guard", name = ["eager-init"], havingValue = "true")
  public open fun guardEagerInitializer(middleware: GuardMiddleware): GuardEagerInitializer =
      GuardEagerInitializer(middleware)

  /**
   * JSON converter ordering guard. The facade requires kotlinx-serialization at runtime, and Spring
   * Framework 6.2's default converter list registers KotlinSerializationJsonHttpMessageConverter
   * BEFORE Jackson - which would silently flip JSON handling for every consuming MVC app. This
   * wiring restores the standard Boot order (Jackson first) whenever both converters are present;
   * an app that wants the kotlinx order overrides this bean (guardJsonConverterOrdering). Framework
   * wiring only - no security behavior.
   */
  @Bean(name = ["guardJsonConverterOrdering"])
  @ConditionalOnClass(
      MappingJackson2HttpMessageConverter::class,
      KotlinSerializationJsonHttpMessageConverter::class,
  )
  public open fun guardJsonConverterOrdering(): WebMvcConfigurer =
      object : WebMvcConfigurer {
        override fun extendMessageConverters(
            converters: MutableList<org.springframework.http.converter.HttpMessageConverter<*>>,
        ) {
          val jacksonIndex = converters.indexOfFirst { it is MappingJackson2HttpMessageConverter }
          val kotlinxIndex = converters.indexOfFirst {
            it is KotlinSerializationJsonHttpMessageConverter
          }
          if (jacksonIndex >= 0 && kotlinxIndex >= 0 && kotlinxIndex < jacksonIndex) {
            val kotlinxConverter = converters.removeAt(kotlinxIndex)
            converters.add(jacksonIndex, kotlinxConverter)
          }
        }
      }
}
