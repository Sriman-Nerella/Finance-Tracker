package com.example.financetracker.data.sync

import com.example.financetracker.data.db.CategoryEntity
import com.example.financetracker.data.db.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SyncResolveTest {

    private fun tx(updatedAt: Long, dirty: Boolean = false, title: String = "t", deletedAt: Long? = null) =
        TransactionEntity(
            id = "1", amountMinor = 1000, currency = "INR", type = "EXPENSE", title = title, note = null,
            categoryId = "c", occurredOn = 20000, createdAt = 1, updatedAt = updatedAt, deletedAt = deletedAt,
            source = "CHAT", dirty = dirty, ownerId = "u",
        )

    @Test fun noLocal_takesRemote() {
        val r = tx(5, title = "remote")
        assertEquals(r, resolve(null, r))
    }

    @Test fun remoteNewer_overwritesCleanLocal() {
        val out = resolve(tx(1, title = "local"), tx(2, title = "remote"))
        assertEquals("remote", out?.title)
        assertFalse(out!!.dirty)
    }

    @Test fun remoteNewer_overwritesOlderDirtyLocal() {
        val out = resolve(tx(1, dirty = true, title = "local"), tx(2, title = "remote"))
        assertEquals("remote", out?.title)
        assertFalse(out!!.dirty)
    }

    @Test fun localNewerDirty_isKept() {
        assertNull(resolve(tx(3, dirty = true), tx(2, title = "remote")))
    }

    @Test fun localNewerClean_isKept() {
        assertNull(resolve(tx(3), tx(2)))
    }

    @Test fun equalTimestamps_keepLocal() {
        assertNull(resolve(tx(2, dirty = true), tx(2, title = "remote")))
        assertNull(resolve(tx(2), tx(2)))
    }

    @Test fun remoteTombstoneNewer_deletesLocal() {
        val out = resolve(tx(1), tx(2, deletedAt = 2))
        assertEquals(2L, out?.deletedAt)
    }

    @Test fun localTombstoneNewer_beatsRemoteEdit() {
        assertNull(resolve(tx(5, dirty = true, deletedAt = 5), tx(4, title = "edited")))
    }

    @Test fun category_sameRules() {
        fun c(u: Long, dirty: Boolean = false) = CategoryEntity("a", "n", "i", "EXPENSE", u, null, dirty, "u")
        assertNull(resolveCategory(c(3, true), c(2)))
        assertEquals(2L, resolveCategory(c(1), c(2))?.updatedAt)
        assertEquals(c(2), resolveCategory(null, c(2)))
    }
}
