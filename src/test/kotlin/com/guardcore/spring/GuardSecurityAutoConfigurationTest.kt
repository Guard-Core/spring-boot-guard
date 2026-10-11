package com.guardcore.spring

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/** The W0 scaffold gate: the auto-configuration type is instantiable without a context. */
public class GuardSecurityAutoConfigurationTest {
  @Test
  public fun instantiatesWithoutSpringContext() {
    val configuration = GuardSecurityAutoConfiguration()
    assertNotNull(configuration)
  }
}
