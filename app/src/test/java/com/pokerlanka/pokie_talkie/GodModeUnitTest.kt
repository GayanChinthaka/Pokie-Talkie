package com.pokerlanka.pokie_talkie

import com.pokerlanka.pokie_talkie.ui.settings.isGodModePasswordValid
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GodModeUnitTest {

    @Test
    fun isGodModePasswordValid_acceptsExactMatchAndRejectsEmptyOrMismatch() {
        assertTrue(isGodModePasswordValid("19930909", "19930909"))
        assertFalse(isGodModePasswordValid("wrong", "19930909"))
        assertFalse(isGodModePasswordValid("", "19930909"))
        assertFalse(isGodModePasswordValid(" 19930909", "19930909"))
        assertFalse(isGodModePasswordValid("", ""))
    }

    @Test
    fun buildConfigGodModePassword_whenConfiguredIsNonBlankAndValidates() {
        val configured = BuildConfig.GOD_MODE_PASSWORD
        if (configured.isNotEmpty()) {
            assertTrue(isGodModePasswordValid(configured, configured))
            assertFalse(isGodModePasswordValid("${configured}_bad", configured))
        }
    }
}
