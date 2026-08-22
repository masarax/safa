package com.safa.account.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncCursorStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("safa_sync_cursor_v1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun cursorAndPermissionScopePersistPerAccount() {
        val store = SyncCursorStore(context)

        store.commit(7, 42L, "scope-a")
        store.commit(8, 9L, "scope-b")

        assertEquals(SyncCursorStore.State(42L, "scope-a"), SyncCursorStore(context).read(7))
        assertEquals(SyncCursorStore.State(9L, "scope-b"), SyncCursorStore(context).read(8))
    }

    @Test
    fun permissionScopeResetForcesUnscopedBootstrapFromZero() {
        val store = SyncCursorStore(context)
        store.commit(7, 42L, "scope-a")

        store.resetForPermissionScope(7, "scope-b")

        assertEquals(SyncCursorStore.State(0L, null), store.read(7))
    }

    @Test
    fun zeroCursorNeverReusesStalePermissionScope() {
        val store = SyncCursorStore(context)

        store.commit(7, 0L, "scope-old")

        assertEquals(SyncCursorStore.State(0L, null), SyncCursorStore(context).read(7))
    }

    @Test(expected = IllegalArgumentException::class)
    fun cursorCannotRegressWithinSameCheckpointHistory() {
        val store = SyncCursorStore(context)
        store.commit(7, 42L, "scope-a")

        store.commit(7, 41L, "scope-a")
    }
}
