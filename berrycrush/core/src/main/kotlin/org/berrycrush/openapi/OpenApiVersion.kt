package org.berrycrush.openapi

/**
 * Supported OpenAPI specification versions.
 */
enum class OpenApiVersion(
    val prefix: String,
    val displayName: String,
) {
    V2_X("2.", "Swagger 2.x"),
    V3_0_X("3.0", "OpenAPI 3.0.x"),
    V3_1_X("3.1", "OpenAPI 3.1.x"),
    V3_2_X("3.2", "OpenAPI 3.2.x"),
    UNKNOWN("", "Unknown"),
    ;

    companion object {
        /**
         * Detect the OpenAPI version from the version string.
         *
         * @param versionString The openapi or swagger version field value (e.g., "3.1.0", "2.0")
         * @return The detected OpenAPI version enum
         */
        fun detect(versionString: String?): OpenApiVersion {
            if (versionString.isNullOrBlank()) return UNKNOWN

            return entries.firstOrNull { it.prefix.isNotEmpty() && versionString.startsWith(it.prefix) }
                ?: UNKNOWN
        }
    }
}
