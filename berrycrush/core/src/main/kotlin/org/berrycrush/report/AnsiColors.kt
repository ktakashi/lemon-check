package org.berrycrush.report

/**
 * ANSI escape code constants and utilities for terminal colorization.
 *
 * Provides helper functions to wrap text with ANSI escape sequences for
 * colored terminal output.
 *
 * ## Usage
 *
 * ```kotlin
 * println(AnsiColors.green("✓ Test passed"))
 * println(AnsiColors.red("✗ Test failed"))
 * println(AnsiColors.bold(AnsiColors.cyan("Custom step executed")))
 * ```
 *
 * ## Nesting
 *
 * Colors can be nested, but inner colors will reset to default when they end.
 * For complex styling, combine attributes before wrapping:
 *
 * ```kotlin
 * println(AnsiColors.wrap("Important", AnsiColors.BOLD + AnsiColors.RED))
 * ```
 */
object AnsiColors {
    // Reset all attributes
    const val RESET = "\u001B[0m"

    // Text styles
    const val BOLD = "\u001B[1m"
    const val DIM = "\u001B[2m"
    const val ITALIC = "\u001B[3m"
    const val UNDERLINE = "\u001B[4m"

    // Standard foreground colors
    const val BLACK = "\u001B[30m"
    const val RED = "\u001B[31m"
    const val GREEN = "\u001B[32m"
    const val YELLOW = "\u001B[33m"
    const val BLUE = "\u001B[34m"
    const val MAGENTA = "\u001B[35m"
    const val CYAN = "\u001B[36m"
    const val WHITE = "\u001B[37m"

    // Bright foreground colors
    const val BRIGHT_BLACK = "\u001B[90m"
    const val BRIGHT_RED = "\u001B[91m"
    const val BRIGHT_GREEN = "\u001B[92m"
    const val BRIGHT_YELLOW = "\u001B[93m"
    const val BRIGHT_BLUE = "\u001B[94m"
    const val BRIGHT_MAGENTA = "\u001B[95m"
    const val BRIGHT_CYAN = "\u001B[96m"
    const val BRIGHT_WHITE = "\u001B[97m"

    // Gray is typically bright black
    const val GRAY = BRIGHT_BLACK

    /**
     * Wrap text with ANSI escape codes.
     *
     * @param text The text to colorize
     * @param code The ANSI escape code(s) to apply
     * @return Text wrapped with escape codes and reset
     */
    fun wrap(
        text: String,
        code: String,
    ): String = "$code$text$RESET"

    /**
     * Apply red color to text.
     */
    fun red(text: String): String = wrap(text, RED)

    /**
     * Apply green color to text.
     */
    fun green(text: String): String = wrap(text, GREEN)

    /**
     * Apply yellow color to text.
     */
    fun yellow(text: String): String = wrap(text, YELLOW)

    /**
     * Apply blue color to text.
     */
    fun blue(text: String): String = wrap(text, BLUE)

    /**
     * Apply cyan color to text.
     */
    fun cyan(text: String): String = wrap(text, CYAN)

    /**
     * Apply gray color to text.
     */
    fun gray(text: String): String = wrap(text, GRAY)

    /**
     * Apply bold style to text.
     */
    fun bold(text: String): String = wrap(text, BOLD)

    /**
     * Apply dim style to text.
     */
    fun dim(text: String): String = wrap(text, DIM)

    /**
     * Apply bold and a color to text.
     */
    fun boldColor(
        text: String,
        color: String,
    ): String = wrap(text, BOLD + color)

    /**
     * Apply bright cyan color to text (useful for highlighting custom steps).
     */
    fun brightCyan(text: String): String = wrap(text, BRIGHT_CYAN)

    /**
     * Apply bright magenta color to text.
     */
    fun brightMagenta(text: String): String = wrap(text, BRIGHT_MAGENTA)
}
