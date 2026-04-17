package org.berrycrush.report

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for AnsiColors utility object.
 */
class AnsiColorsTest {
    @Test
    fun `red wraps text with red color codes`() {
        val result = AnsiColors.red("error")
        assertEquals("\u001B[31merror\u001B[0m", result)
    }

    @Test
    fun `green wraps text with green color codes`() {
        val result = AnsiColors.green("success")
        assertEquals("\u001B[32msuccess\u001B[0m", result)
    }

    @Test
    fun `yellow wraps text with yellow color codes`() {
        val result = AnsiColors.yellow("warning")
        assertEquals("\u001B[33mwarning\u001B[0m", result)
    }

    @Test
    fun `gray wraps text with gray (bright black) color codes`() {
        val result = AnsiColors.gray("dimmed")
        assertEquals("\u001B[90mdimmed\u001B[0m", result)
    }

    @Test
    fun `bold wraps text with bold style`() {
        val result = AnsiColors.bold("important")
        assertEquals("\u001B[1mimportant\u001B[0m", result)
    }

    @Test
    fun `dim wraps text with dim style`() {
        val result = AnsiColors.dim("faded")
        assertEquals("\u001B[2mfaded\u001B[0m", result)
    }

    @Test
    fun `wrap applies custom code`() {
        val result = AnsiColors.wrap("text", AnsiColors.UNDERLINE)
        assertEquals("\u001B[4mtext\u001B[0m", result)
    }

    @Test
    fun `boldColor combines bold and color`() {
        val result = AnsiColors.boldColor("text", AnsiColors.CYAN)
        assertEquals("\u001B[1m\u001B[36mtext\u001B[0m", result)
    }

    @Test
    fun `brightCyan wraps text with bright cyan`() {
        val result = AnsiColors.brightCyan("highlighted")
        assertEquals("\u001B[96mhighlighted\u001B[0m", result)
    }

    @Test
    fun `empty text returns empty with codes`() {
        val result = AnsiColors.red("")
        assertEquals("\u001B[31m\u001B[0m", result)
    }

    @Test
    fun `RESET constant is correct`() {
        assertEquals("\u001B[0m", AnsiColors.RESET)
    }

    @Test
    fun `all color constants start with escape sequence`() {
        val colors =
            listOf(
                AnsiColors.RED,
                AnsiColors.GREEN,
                AnsiColors.YELLOW,
                AnsiColors.BLUE,
                AnsiColors.CYAN,
                AnsiColors.MAGENTA,
                AnsiColors.WHITE,
                AnsiColors.BLACK,
                AnsiColors.GRAY,
                AnsiColors.BRIGHT_RED,
                AnsiColors.BRIGHT_GREEN,
                AnsiColors.BRIGHT_YELLOW,
                AnsiColors.BRIGHT_BLUE,
                AnsiColors.BRIGHT_CYAN,
                AnsiColors.BRIGHT_MAGENTA,
                AnsiColors.BRIGHT_WHITE,
            )
        for (color in colors) {
            assertTrue(color.startsWith("\u001B["), "Color should start with escape: $color")
            assertTrue(color.endsWith("m"), "Color should end with m: $color")
        }
    }
}
