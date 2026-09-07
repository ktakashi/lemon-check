package org.berrycrush.util

import org.berrycrush.configuration.Configuration
import org.berrycrush.model.HttpRequest
import org.berrycrush.model.HttpResponse
import org.berrycrush.plugin.ExecutionContext
import org.berrycrush.plugin.ScenarioContext
import org.berrycrush.plugin.StepContext
import org.berrycrush.plugin.StepOperation
import org.berrycrush.plugin.adapter.ExecutionContextAdapter
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import org.berrycrush.context.ExecutionContext as CoreExecutionContext

fun createStepContext(context: ExecutionContext = ExecutionContextAdapter(CoreExecutionContext())) =
    object : StepContext {
        override val stepDescription: String
            get() = TODO("Not yet implemented")
        override val stepType: org.berrycrush.plugin.StepType
            get() = TODO("Not yet implemented")
        override val stepIndex: Int
            get() = TODO("Not yet implemented")
        override val scenarioContext: ScenarioContext
            field =
            object : ScenarioContext {
                override val scenarioName: String
                    get() = TODO("Not yet implemented")
                override val scenarioFile: Path
                    get() = TODO("Not yet implemented")
                override val variables: MutableMap<String, Any>
                    get() = TODO("Not yet implemented")
                override val metadata: Map<String, String>
                    get() = TODO("Not yet implemented")
                override val startTime: Instant
                    get() = TODO("Not yet implemented")
                override val tags: Set<String>
                    get() = TODO("Not yet implemented")
                override val audits: List<ScenarioContext.HttpAudit>
                    get() = TODO("Not yet implemented")
                override val executionContext: ExecutionContext
                    get() = context
                override val operations: List<StepOperation>
                    get() = listOf()
                override val configuration: Configuration
                    get() = TODO("Not yet implemented")
            }
        override val request: HttpRequest
            get() = TODO("Not yet implemented")
        override val response: HttpResponse? = null
        override val operationId: String
            get() = TODO("Not yet implemented")
        override val responseTime: Duration
            get() = TODO("Not yet implemented")
        override val operation: StepOperation?
            get() = null
        override val configuration: Configuration
            get() = scenarioContext.configuration
    }
