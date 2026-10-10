package com.guardcore.spring

import kotlin.test.Test
import kotlin.test.assertNotNull

class GuardSecurityAutoConfigurationTest {

  @Test
  fun instantiatesWithoutSpringContext() {
    val configuration = GuardSecurityAutoConfiguration()
    assertNotNull(configuration)
  }
}
