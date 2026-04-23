package com.vitbon.kkm.core.fiscal.runtime

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class FfdPolicyStoreTest {

    @Test
    fun `saveResolved stores version source and lock flag`() {
        val prefs = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.apply() } just runs

        val store = FfdPolicyStore(prefs)
        store.saveResolved(version = "1.2", source = "auto", locked = true, timestampMs = 1000L)

        verify { editor.putString("ffd.version", "1.2") }
        verify { editor.putString("ffd.source", "auto") }
        verify { editor.putBoolean("ffd.locked", true) }
        verify { editor.putLong("ffd.updatedAt", 1000L) }
    }

    @Test
    fun `read returns defaults when prefs empty`() {
        val prefs = mockk<SharedPreferences>()
        every { prefs.getString("ffd.version", null) } returns null
        every { prefs.getString("ffd.source", "unknown") } returns "unknown"
        every { prefs.getBoolean("ffd.locked", false) } returns false
        every { prefs.getLong("ffd.updatedAt", 0L) } returns 0L

        val store = FfdPolicyStore(prefs)
        val state = store.read()

        assertEquals(null, state.version)
        assertEquals("unknown", state.source)
        assertEquals(false, state.locked)
        assertEquals(0L, state.updatedAt)
    }
}
