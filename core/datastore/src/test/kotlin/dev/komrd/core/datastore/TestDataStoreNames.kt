package dev.komrd.core.datastore

import java.util.concurrent.atomic.AtomicInteger

private val counter = AtomicInteger()

internal fun testDataStoreName(prefix: String): String = "${prefix}_${counter.incrementAndGet()}"
