package com.safa.account.data.sync

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SyncCoordinatorTest {

    @Test
    fun serializesConcurrentReconciliationBlocks() = runBlocking {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)

        suspend fun criticalWork(): Int? = SyncCoordinator.run {
            val now = active.incrementAndGet()
            maxActive.updateAndGet { maxOf(it, now) }
            delay(75)
            active.decrementAndGet()
            1
        }

        val first = async { criticalWork() }
        val second = async { criticalWork() }

        assertNotNull(first.await())
        assertNotNull(second.await())
        assertEquals(1, maxActive.get())
    }
}
