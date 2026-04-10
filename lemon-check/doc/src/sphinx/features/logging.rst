HTTP Logging
============

LemonCheck provides configurable HTTP request and response logging
to help with debugging and monitoring API tests.

Enabling Logging
----------------

You can enable logging via:

1. **File-level parameters** in scenario files
2. **Programmatic configuration** in bindings

File-Level Parameters
^^^^^^^^^^^^^^^^^^^^^

.. code-block:: text

   # In your .scenario file
   parameters:
     logRequests: true
     logResponses: true

   scenario: Test with logging
     given I call the API
       call ^getUser
         userId: 123
     then I see the response
       assert status 200

Programmatic Configuration
^^^^^^^^^^^^^^^^^^^^^^^^^^

In your bindings class:

.. code-block:: java

   @Component
   public class MyBindings implements LemonCheckBindings {
       @Override
       public void configure(Configuration config) {
           config.setLogRequests(true);
           config.setLogResponses(true);
       }
   }

Or in Kotlin DSL:

.. code-block:: kotlin

   lemonCheck {
       configure {
           logRequests = true
           logResponses = true
       }
       // ... scenarios
   }

Log Output Format
-----------------

Default Format
^^^^^^^^^^^^^^

The default logger uses a human-readable multi-line format:

Request log::

   ▶ HTTP Request
     POST http://localhost:8080/api/users
     Headers: {Content-Type=application/json, Authorization=***}
     Body: {"name": "John"}

Response log::

   ◀ HTTP Response [200 OK] (125ms)
     POST http://localhost:8080/api/users
     Headers: {Content-Type=application/json}
     Body: {"id": 123, "name": "John"}

Note that sensitive headers (Authorization, X-Api-Key, Cookie) are automatically
masked with ``***`` for security.

Compact Format
^^^^^^^^^^^^^^

For less verbose output, use the compact formatter:

.. code-block:: kotlin

   import io.github.ktakashi.lemoncheck.logging.CompactHttpLogFormatter
   import io.github.ktakashi.lemoncheck.logging.ConsoleHttpLogger

   config.httpLogger = ConsoleHttpLogger(CompactHttpLogFormatter())

Compact output::

   ▶ POST /api/users {body: 50 chars}
   ◀ 200 OK (125ms) {body: 100 chars}

Custom Logging
--------------

Custom Logger Implementation
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Implement the ``HttpLogger`` interface for custom logging behavior:

.. code-block:: kotlin

   class Slf4jHttpLogger : HttpLogger {
       private val logger = LoggerFactory.getLogger("http")

       override fun logRequest(
           method: HttpMethod,
           url: String,
           headers: Map<String, String>,
           body: String?
       ) {
           logger.info("→ {} {}", method, url)
       }

       override fun logResponse(
           method: HttpMethod,
           url: String,
           response: HttpResponse<String>,
           durationMs: Long
       ) {
           logger.info("← {} ({} ms)", response.statusCode(), durationMs)
       }
   }

   // Use in configuration
   config.httpLogger = Slf4jHttpLogger()

Custom Formatter
^^^^^^^^^^^^^^^^

Customize the log message format by implementing ``HttpLogFormatter``:

.. code-block:: kotlin

   class JsonLogFormatter : HttpLogFormatter {
       private val mapper = ObjectMapper()

       override fun formatRequest(
           method: HttpMethod,
           url: String,
           headers: Map<String, String>,
           body: String?
       ): String {
           return mapper.writeValueAsString(mapOf(
               "type" to "request",
               "method" to method.name,
               "url" to url,
               "body_length" to (body?.length ?: 0)
           ))
       }

       override fun formatResponse(
           method: HttpMethod,
           url: String,
           response: HttpResponse<String>,
           durationMs: Long
       ): String {
           return mapper.writeValueAsString(mapOf(
               "type" to "response",
               "status" to response.statusCode(),
               "duration_ms" to durationMs
           ))
       }
   }

   // Use with console logger
   config.httpLogger = ConsoleHttpLogger(JsonLogFormatter())

Global Logger Factory
^^^^^^^^^^^^^^^^^^^^^

Set a global default logger for all tests:

.. code-block:: kotlin

   // In test setup
   HttpLoggerFactory.setFactory { Slf4jHttpLogger() }

   // Or use built-in JUL logger
   HttpLoggerFactory.useJulLogger()

   // Reset to default console logger
   HttpLoggerFactory.resetToDefault()

DefaultHttpLogFormatter Options
-------------------------------

The default formatter supports several configuration options:

.. code-block:: kotlin

   val formatter = DefaultHttpLogFormatter(
       includeHeaders = true,       // Include headers in output
       includeBody = true,          // Include request/response body
       maxBodyLength = 1000,        // Truncate body after N chars
       maskSensitiveHeaders = true  // Mask Authorization, etc.
   )

Masked Header Names
^^^^^^^^^^^^^^^^^^^

These headers are automatically masked when ``maskSensitiveHeaders`` is true:

- authorization
- x-api-key
- api-key
- cookie
- set-cookie
- x-auth-token

Logger Types
------------

LemonCheck provides three logger implementations:

.. list-table::
   :header-rows: 1
   :widths: 25 75

   * - Logger
     - Description
   * - ``ConsoleHttpLogger``
     - Prints to stdout (default). Simple and always works.
   * - ``JulHttpLogger``
     - Uses java.util.logging. Good for integration with existing JUL config.
   * - Custom
     - Implement ``HttpLogger`` for SLF4J, Log4j, etc.
