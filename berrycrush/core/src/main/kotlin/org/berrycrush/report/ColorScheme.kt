package org.berrycrush.report

import org.berrycrush.plugin.ResultStatus

/**
 * Color scheme configuration for console report output.
 *
 * Defines ANSI color codes to use for different result statuses and
 * special highlighting (e.g., custom steps/assertions).
 *
 * ## Usage
 *
 * ```kotlin
 * // Use default colors
 * val scheme = ColorScheme.DEFAULT
 *
 * // Custom scheme
 * val customScheme = ColorScheme(
 *     passed = AnsiColors.BRIGHT_GREEN,
 *     failed = AnsiColors.BRIGHT_RED,
 *     customHighlight = AnsiColors.BOLD + AnsiColors.BRIGHT_MAGENTA,
 * )
 * ```
 *
 * @property passed Color for passed steps/assertions
 * @property failed Color for failed steps/assertions
 * @property skipped Color for skipped steps (not executed)
 * @property error Color for steps that had errors
 * @property customHighlight Color/style for custom steps and assertions
 * @property header Color for report headers and separators
 * @property stepDescription Color for step description text (default)
 */
data class ColorScheme(
    val passed: String = AnsiColors.GREEN,
    val failed: String = AnsiColors.RED,
    val skipped: String = AnsiColors.GRAY,
    val error: String = AnsiColors.YELLOW,
    val customHighlight: String = AnsiColors.BOLD + AnsiColors.BRIGHT_CYAN,
    val header: String = AnsiColors.BOLD,
    val stepDescription: String = "",
) {
    companion object {
        /**
         * Default color scheme with standard terminal colors.
         */
        val DEFAULT = ColorScheme()

        /**
         * High contrast scheme for accessibility.
         */
        val HIGH_CONTRAST = ColorScheme(
            passed = AnsiColors.BOLD + AnsiColors.GREEN,
            failed = AnsiColors.BOLD + AnsiColors.RED,
            skipped = AnsiColors.DIM,
            error = AnsiColors.BOLD + AnsiColors.YELLOW,
            customHighlight = AnsiColors.BOLD + AnsiColors.BRIGHT_MAGENTA,
            header = AnsiColors.BOLD + AnsiColors.BRIGHT_WHITE,
            stepDescription = AnsiColors.WHITE,
        )

        /**
         * Monochrome scheme using only styles (bold, dim) without colors.
         */
        val MONOCHROME = ColorScheme(
            passed = "",
            failed = AnsiColors.BOLD,
            skipped = AnsiColors.DIM,
            error = AnsiColors.BOLD + AnsiColors.UNDERLINE,
            customHighlight = AnsiColors.BOLD,
            header = AnsiColors.BOLD,
            stepDescription = "",
        )

        /**
         * No colors - identity scheme for testing or piping.
         */
        val NONE = ColorScheme(
            passed = "",
            failed = "",
            skipped = "",
            error = "",
            customHighlight = "",
            header = "",
            stepDescription = "",
        )
    }

    /**
     * Get the color code for a given result status.
     */
    fun forStatus(status: ResultStatus): String =
        when (status) {
            ResultStatus.PASSED -> passed
            ResultStatus.FAILED -> failed
            ResultStatus.SKIPPED -> skipped
            ResultStatus.ERROR -> error
        }

    /**
     * Apply color for the given status to text.
     *
     * @param text The text to colorize
     * @param status The result status determining color
     * @return Colorized text with reset at end
     */
    fun colorize(text: String, status: ResultStatus): String {
        val color = forStatus(status)
        return if (color.isEmpty()) text else AnsiColors.wrap(text, color)
    }

    /**
     * Apply custom highlighting to text (for custom steps/assertions).
     *
     * @param text The text to highlight
     * @return Highlighted text with reset at end
     */
    fun highlight(text: String): String =
        if (customHighlight.isEmpty()) text else AnsiColors.wrap(text, customHighlight)

    /**
     * Apply header styling to text.
     *
     * @param text The header text
     * @return Styled header text with reset at end
     */
    fun headerStyle(text: String): String =
        if (header.isEmpty()) text else AnsiColors.wrap(text, header)
}
