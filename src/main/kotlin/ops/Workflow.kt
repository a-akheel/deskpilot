package ops

import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.failure.ActivityFailure
import io.temporal.failure.ApplicationFailure
import io.temporal.workflow.*
import java.time.Duration

@WorkflowInterface
interface IncidentWorkflow {
    @WorkflowMethod fun run(request: Request): String
    @SignalMethod fun decide(approved: Boolean, planId: String)
    @QueryMethod fun snapshot(): Snapshot
}

class IncidentWorkflowImpl : IncidentWorkflow {
    private val ops = Workflow.newActivityStub(
        Operations::class.java,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(3).setInitialInterval(Duration.ofSeconds(1)).build())
            .build(),
    )
    private var state = "queued"
    private var proposed: Plan? = null
    private var decision: Boolean? = null
    private var failure: String? = null
    private val events = mutableListOf<String>()

    private fun transition(value: String) {
        state = value
        events.add("${Workflow.currentTimeMillis()}: $value")
    }

    override fun snapshot() = Snapshot(state, proposed, events.toList(), failure)

    override fun decide(approved: Boolean, planId: String) {
        if (state == "awaiting_approval" && decision == null && proposed?.id == planId) {
            decision = approved
            events.add("${Workflow.currentTimeMillis()}: decision=${if (approved) "approve" else "reject"}")
        }
    }

    override fun run(request: Request): String {
        try {
            if (request.service !in allowedServices) {
                proposed = Plan(reason = "Select one affected service before investigation")
                transition("escalated")
                return state
            }
            transition("investigating")
            val initial = ops.investigate(request)
            proposed = ops.plan(request, initial).copy(id = Workflow.randomUUID().toString())
            if (!initial.permitsRestart() || proposed!!.action != "restart_service") {
                transition("escalated")
                return state
            }
            transition("awaiting_approval")
            val received = Workflow.await(Duration.ofHours(1)) { decision != null }
            if (!received || decision != true) {
                transition(if (received) "rejected" else "expired")
                return state
            }
            transition("revalidating")
            val current = ops.investigate(request)
            if (!current.permitsRestart() || current.revision != initial.revision) {
                transition("stale_approval")
                return state
            }
            transition("executing")
            ops.execute(request.service, Workflow.getInfo().workflowId + ":restart")
            transition("completed")
        } catch (e: ActivityFailure) {
            failure = (e.cause as? ApplicationFailure)?.type ?: "ACTIVITY_FAILED"
            transition("failed")
        }
        return state
    }
}
