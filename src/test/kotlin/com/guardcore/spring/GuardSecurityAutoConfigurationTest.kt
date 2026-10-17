package com.guardcore.spring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.web.servlet.FilterRegistrationBean

/** The W0 scaffold gate: the auto-configuration type is instantiable without a context. */
public class GuardSecurityAutoConfigurationTest {
  @Test
  public fun instantiatesWithoutSpringContext() {
    val configuration = GuardSecurityAutoConfiguration()
    assertNotNull(configuration)
  }
}

/** `guard.enabled`: on when missing or true, nothing registered when false. */
public class GuardEnabledPropertyTest {
  private val runner =
      WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(GuardSecurityAutoConfiguration::class.java))
          .withPropertyValues("guard.enable-redis=false")

  @Test
  public fun enabledWhenPropertyIsMissing() {
    runner.run { context ->
      assertThat(context).hasSingleBean(GuardMiddleware::class.java)
      assertThat(context).hasSingleBean(GuardProperties::class.java)
    }
  }

  @Test
  public fun enabledWhenPropertyIsTrue() {
    runner.withPropertyValues("guard.enabled=true").run { context ->
      assertThat(context).hasSingleBean(GuardMiddleware::class.java)
    }
  }

  @Test
  public fun nothingRegisteredWhenPropertyIsFalse() {
    runner.withPropertyValues("guard.enabled=false").run { context ->
      assertThat(context).doesNotHaveBean(GuardSecurityAutoConfiguration::class.java)
      assertThat(context).doesNotHaveBean(GuardProperties::class.java)
      assertThat(context).doesNotHaveBean(GuardMiddleware::class.java)
      assertThat(context).doesNotHaveBean(SpringGuardDecorator::class.java)
      assertThat(context).doesNotHaveBean(AnnotationRouteConfigurator::class.java)
      assertThat(context.getBeansOfType(FilterRegistrationBean::class.java)).isEmpty()
    }
  }
}
