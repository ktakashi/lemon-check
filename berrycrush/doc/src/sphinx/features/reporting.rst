Reporting
=========

BerryCrush provides multiple report formats to integrate with your CI/CD pipeline
and development workflow.

Report Formats
--------------

BerryCrush includes four built-in report formats:

Text Report
^^^^^^^^^^^

Human-readable console output, ideal for development and debugging.

.. code-block:: text

    ═══════════════════════════════════════════════════════════════════════════════
    BerryCrush Test Report
    ═══════════════════════════════════════════════════════════════════════════════
    Execution Date: 2026-04-09T10:30:00Z
    Duration: 1,234s

    scenario: List all pets ✓
      I request all pets ................................. 200 OK
      I verify the response .............................. 200 OK

    scenario: Create a new pet ✓
      I create a pet ................................ 201 Created
      the pet is created ............................ 201 Created

    ═══════════════════════════════════════════════════════════════════════════════
    Summary: 2/2 scenarios passed (100.0%)
      2 passed, 0 failed, 0 skipped, 0 errors
    ═══════════════════════════════════════════════════════════════════════════════

Configuration:

.. code-block:: kotlin

    @BerryCrushConfiguration(
        pluginClasses = [TextReportPlugin::class]
    )

Console Report (Colored)
^^^^^^^^^^^^^^^^^^^^^^^^

ANSI-colored console output for terminal display. Provides visual highlighting
with green for passed, red for failed, gray for skipped, and bold cyan for
custom steps/assertions.

.. code-block:: text

    ═══════════════════════════════════════════════════════════════════════════════
    BerryCrush Test Report
    ═══════════════════════════════════════════════════════════════════════════════
    Execution Date: 2026-04-09T10:30:00Z
    Duration: 1.234s

    scenario: List all pets ✓    (green checkmark)
      I request all pets ................................. 200 OK
      verify custom assertion ............................ pass    (bold cyan + green)

    scenario: Invalid request ✗  (red X)
      assert status 200 ................................. FAIL    (red)

Features:

- **Color-coded results**: Green for passed, red for failed, gray for skipped
- **Custom step highlighting**: Bold/bright cyan for custom steps and assertions
- **Configurable colors**: Customize via ``ColorScheme``
- **Multiple schemes**: DEFAULT, HIGH_CONTRAST, MONOCHROME, NONE

Configuration:

.. code-block:: kotlin

    @BerryCrushConfiguration(
        pluginClasses = [ConsoleReportPlugin::class]
    )

With options (output stream, color scheme):

.. code-block:: kotlin

    @BerryCrushConfiguration(
        // stderr with high contrast colors
        plugins = ["report:console:stderr,high-contrast"]
    )

Supported options (comma-separated):

- ``stderr`` - Output to stderr instead of stdout
- ``stdout`` - Output to stdout (default)
- ``high-contrast`` - Bold colors for accessibility
- ``monochrome`` or ``mono`` - Styles only (bold, dim), no colors
- ``no-color`` or ``none`` - Plain text, no styling

Examples:

.. code-block:: kotlin

    plugins = ["report:console"]                    // stdout, default colors
    plugins = ["report:console:stderr"]             // stderr, default colors
    plugins = ["report:console:high-contrast"]      // stdout, high contrast
    plugins = ["report:console:stderr,monochrome"]  // stderr, styles only

With custom colors:

.. code-block:: kotlin

    // In bindings
    override fun getPlugins() = listOf(
        ConsoleReportPlugin(
            output = System.err,            // Write to stderr
            colorScheme = ColorScheme.HIGH_CONTRAST
        )
    )

Available color schemes:

- ``ColorScheme.DEFAULT`` - Standard terminal colors
- ``ColorScheme.HIGH_CONTRAST`` - Bold colors for accessibility
- ``ColorScheme.MONOCHROME`` - Styles only (bold, dim), no colors
- ``ColorScheme.NONE`` - No styling (plain text)

JSON Report
^^^^^^^^^^^

Machine-parseable JSON format, suitable for custom integrations.

A JSON Schema (2020-12) is available at ``berrycrush-report.schema.json`` in the
berrycrush-core resources for validation.

.. code-block:: json

    {
      "timestamp": "2026-04-09T10:30:00Z",
      "duration": 1234,
      "summary": {
        "total": 5,
        "passed": 4,
        "failed": 1,
        "skipped": 0,
        "errors": 0
      },
      "scenarios": [
        {
          "name": "List all pets",
          "status": "PASSED",
          "duration": 123,
          "tags": ["api", "pets"],
          "steps": [
            {
              "description": "I request all pets",
              "status": "PASSED",
              "duration": 45,
              "response": {
                "statusCode": 200,
                "statusMessage": "OK",
                "headers": { "content-type": ["application/json"] },
                "body": "{...}",
                "duration": 45,
                "timestamp": "2026-04-09T10:30:00.123Z"
              }
            }
          ]
        }
      ],
      "environment": {}
    }

Configuration:

.. code-block:: kotlin

    @BerryCrushConfiguration(
        plugins = ["report:json:build/reports/berrycrush.json"]
    )

Or programmatically:

.. code-block:: kotlin

    @BerryCrushConfiguration(
        pluginClasses = [JsonReportPlugin::class]
    )

JUnit XML Report
^^^^^^^^^^^^^^^^

JUnit XML format for CI/CD integration (Jenkins, GitHub Actions, GitLab CI, etc.).

.. code-block:: xml

    <?xml version="1.0" encoding="UTF-8"?>
    <testsuites name="BerryCrush" tests="5" failures="1" errors="0" time="1.234">
      <testsuite name="Pet API" tests="3" failures="1">
        <testcase name="List all pets" classname="scenarios.pet-api" time="0.123"/>
        <testcase name="Create a pet" classname="scenarios.pet-api" time="0.456">
          <failure message="Expected status 201 but got 400" type="AssertionError">
            Expected: 201
            Actual: 400
            
            Request: POST /api/pets
            Response: 400 Bad Request
          </failure>
        </testcase>
      </testsuite>
    </testsuites>

Configuration:

.. code-block:: kotlin

    @BerryCrushConfiguration(
        plugins = ["report:junit:build/test-results/berrycrush.xml"]
    )

XML Report
^^^^^^^^^^

Generic XML format for custom processing.

Configuration:

.. code-block:: kotlin

    @BerryCrushConfiguration(
        pluginClasses = [XmlReportPlugin::class]
    )

Configuring Reports
-------------------

Via Annotation
^^^^^^^^^^^^^^

.. code-block:: kotlin

    @BerryCrushConfiguration(
        pluginClasses = [
            ConsoleReportPlugin::class, // Colored console output
            JunitReportPlugin::class    // CI/CD integration
        ],
        plugins = [
            "report:json:reports/test-results.json"
        ]
    )

Via Bindings
^^^^^^^^^^^^

.. code-block:: kotlin

    class MyBindings : BerryCrushBindings {
        override fun getPlugins(): List<BerryCrushPlugin> {
            return listOf(
                TextReportPlugin(),
                JsonReportPlugin("build/reports/berrycrush.json"),
                JunitReportPlugin("build/test-results/TEST-berrycrush.xml")
            )
        }
    }

Report Output Locations
-----------------------

By default, reports are written to:

* JSON: ``build/reports/berrycrush-report.json``
* JUnit: ``build/test-results/TEST-berrycrush.xml``
* Text: Console output (stdout)

Customize paths via constructor arguments:

.. code-block:: kotlin

    JsonReportPlugin("custom/path/report.json")
    JunitReportPlugin("custom/path/junit.xml")

CI/CD Integration
-----------------

GitHub Actions
^^^^^^^^^^^^^^

.. code-block:: yaml

    - name: Run API tests
      run: ./gradlew test

    - name: Publish Test Results
      uses: EnricoMi/publish-unit-test-result-action@v2
      if: always()
      with:
        files: build/test-results/**/*.xml

Jenkins
^^^^^^^

.. code-block:: groovy

    post {
        always {
            junit 'build/test-results/**/*.xml'
        }
    }

GitLab CI
^^^^^^^^^

.. code-block:: yaml

    test:
      script:
        - ./gradlew test
      artifacts:
        reports:
          junit: build/test-results/**/*.xml

Custom Report Plugins
---------------------

Create custom reports by extending ``ReportPlugin``:

.. code-block:: kotlin

    class HtmlReportPlugin(
        private val outputPath: String = "build/reports/berrycrush.html"
    ) : ReportPlugin("html") {

        override fun formatReport(report: TestReport): String {
            return buildString {
                appendLine("<!DOCTYPE html>")
                appendLine("<html>")
                appendLine("<head><title>BerryCrush Report</title></head>")
                appendLine("<body>")
                
                for (scenario in report.scenarios) {
                    appendLine("<h2>${scenario.name}</h2>")
                    appendLine("<ul>")
                    for (step in scenario.steps) {
                        val icon = when (step.status) {
                            ResultStatus.PASSED -> "✓"
                            ResultStatus.FAILED -> "✗"
                            else -> "○"
                        }
                        appendLine("<li>$icon ${step.description}</li>")
                    }
                    appendLine("</ul>")
                }
                
                appendLine("</body>")
                appendLine("</html>")
            }
        }

        override fun onReportGenerated(content: String) {
            File(outputPath).writeText(content)
        }
    }

Best Practices
--------------

1. **Use JUnit XML for CI/CD**: Most CI tools understand this format
2. **Generate JSON for dashboards**: Easy to parse and visualize
3. **Use Text for debugging**: Immediate feedback during development
4. **Set output paths explicitly**: Avoid conflicts with other tools
5. **Include timing data**: Helps identify slow tests
