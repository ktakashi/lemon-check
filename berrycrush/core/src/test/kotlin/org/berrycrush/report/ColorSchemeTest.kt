package org.berrycrush.report

import org.berrycrush.plugin.ResultStatus
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for ColorScheme configuration class.
 */
class ColorSchemeTest {
    @Test
    fun `DEFAULT scheme has green for passed`() {
        val scheme = ColorScheme.DEFAULT
        assertEquals(AnsiColors.GREEN, scheme.passed)
    }

    @Test
    fun `DEFAULT scheme has red for failed`() {
        val scheme = ColorScheme.DEFAULT
        assertEquals(AnsiColors.RED, scheme.failed)
    }

    @Test
    fun `DEFAULT scheme has gray for skipped`() {
        val scheme = ColorScheme.DEFAULT
        assertEquals(AnsiColors.GRAY, scheme.skipped)
    }

    @Test
    fun `DEFAULT scheme has yellow for error`() {
        val scheme = ColorScheme.DEFAULT
        assertEquals(AnsiColors.YELLOW, scheme.error)
    }

    @Test
    fun `NONE scheme has empty strings for all colors`() {
        val scheme = ColorScheme.NONE
        assertEquals("", scheme.passed)
        assertEquals("", scheme.failed)
        assertEquals("", scheme.skipped)
        assertEquals("", scheme.error)
        assertEquals("", scheme.customHighlight)
        assertEquals("", scheme.header)
    }

    @Test
    fun `forStatus returns correct color for each status`() {
        val scheme = ColorScheme.DEFAULT
        assertEquals(AnsiColors.GREEN, scheme.forStatus(ResultStatus.PASSED))
        assertEquals(AnsiColors.RED, scheme.forStatus(ResultStatus.FAILED))
        assertEquals(AnsiColors.GRAY, scheme.forStatus(ResultStatus.SKIPPED))
        assertEquals(AnsiColors.YELLOW, scheme.forStatus(ResultStatus.ERROR))
    }

    @Test
    fun `colorize wraps text with appropriate color`() {
        val scheme = ColorScheme.DEFAULT
        val result = scheme.colorize("pass", ResultStatus.PASSED)
        assertTrue(result.contains(AnsiColors.GREEN))
        assertTrue(result.contains(AnsiColors.RESET))
        assertTrue(result.contains("pass"))
    }

    @Test
    fun `colorize with NONE scheme returns unchanged text`() {
        val scheme = ColorScheme.NONE
        val result = scheme.colorize("text", ResultStatus.PASSED)
        assertEquals("text", result)
    }

    @Test
    fun `highlight wraps text with custom highlight color`() {
        val scheme = ColorScheme.DEFAULT
        val result = scheme.highlight("custom step")
        assertTrue(result.contains("custom step"))
        assertTrue(result.contains(AnsiColors.BOLD))
        assertTrue(result.contains(AnsiColors.BRIGHT_CYAN))
    }

    @Test
    fun `highlight with NONE scheme returns unchanged text`() {
        val scheme = ColorScheme.NONE
        val result = scheme.highlight("custom step")
        assertEquals("custom step", result)
    }

    @Test
    fun `headerStyle applies header color`() {
        val scheme = ColorScheme.DEFAULT
        val result = scheme.headerStyle("Header")
        assertTrue(result.contains(AnsiColors.BOLD))
        assertTrue(result.contains("Header"))
    }

    @Test
    fun `custom scheme allows custom colors`() {
        val customScheme = ColorScheme(
            passed = AnsiColors.BRIGHT_GREEN,
            failed = AnsiColors.BRIGHT_RED,
        )
        assertEquals(AnsiColors.BRIGHT_GREEN, customScheme.passed)
        assertEquals(AnsiColors.BRIGHT_RED, customScheme.failed)
    }

    @Test
    fun `HIGH_CONTRAST scheme has bold colors`() {
        val scheme = ColorScheme.HIGH_CONTRAST
        assertTrue(scheme.passed.contains(AnsiColors.BOLD))
        assertTrue(scheme.failed.contains(AnsiColors.BOLD))
        assertTrue(scheme.error.contains(AnsiColors.BOLD))
    }

    @Test
    fun `MONOCHROME scheme uses only styles`() {
        val scheme = ColorScheme.MONOCHROME
        // Monochrome uses bold for failed, dim for skipped
        assertEquals("", scheme.passed)
        assertEquals(AnsiColors.BOLD, scheme.failed)
        assertEquals(AnsiColors.DIM, scheme.skipped)
        // Should not contain color codes
        assertFalse(scheme.failed.contains(AnsiColors.RED))
    }
}
