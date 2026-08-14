package com.safa.account.data.sync

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

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

    @Test
    fun lifecycleExclusiveGateCannotRaceAnActiveSync() = runBlocking {
        val enteredSync = CompletableDeferred<Unit>()
        val releaseSync = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()

        val sync = async {
            SyncCoordinator.run {
                order += "sync-start"
                enteredSync.complete(Unit)
                releaseSync.await()
                order += "sync-end"
            }
        }
        enteredSync.await()

        val lifecycle = async {
            SyncCoordinator.runExclusive {
                order += "lifecycle"
            }
        }

        delay(50)
        assertFalse(lifecycle.isCompleted)
        releaseSync.complete(Unit)
        sync.await()
        lifecycle.await()

        assertEquals(listOf("sync-start", "sync-end", "lifecycle"), order)
    }
}
