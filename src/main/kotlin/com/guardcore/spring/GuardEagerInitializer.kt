package com.guardcore.spring

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener

/**
 * The guard.lifespan.guard_lifespan analog (fastapi-guard lifespan.py): opt-in boot-time
 * initialization of the guard middleware, registered only when `guard.eager-init=true`. Without it,
 * the middleware initializes lazily on the first request behind a lock (the fastapi-guard default),
 * and a lazy_init=False config logs the "eager init not honored" warning.
 */
public class GuardEagerInitializer(
    private val middleware: GuardMiddleware,
) : ApplicationListener<ApplicationReadyEvent> {
  override fun onApplicationEvent(event: ApplicationReadyEvent) {
    kotlinx.coroutines.runBlocking { middleware.ensureInitialized() }
  }
}
