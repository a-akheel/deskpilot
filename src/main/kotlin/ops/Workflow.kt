package ops

import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.workflow.*
import java.time.Duration

data class Request(val service: String = "", val description: String = "")
data class Context(val service: String = "", val status: String = "", val evidence: String = "")
data class Plan(val action: String = "escalate", val reason: String = "", val evidence: String = "", val tokens: Int = 0, val latencyMs: Long = 0)
data class Snapshot(val status: String = "queued", val plan: Plan? = null, val events: List<String> = emptyList())
@ActivityInterface interface Operations {
 fun investigate(request: Request): Context
 fun plan(request: Request, context: Context): Plan
 fun execute(service: String, key: String): String
}
@WorkflowInterface interface IncidentWorkflow {
 @WorkflowMethod fun run(request: Request): String
 @SignalMethod fun decide(approved: Boolean)
 @QueryMethod fun snapshot(): Snapshot
}
class IncidentWorkflowImpl : IncidentWorkflow {
 private val ops = Workflow.newActivityStub(Operations::class.java, ActivityOptions.newBuilder()
  .setStartToCloseTimeout(Duration.ofSeconds(30))
  .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).setInitialInterval(Duration.ofSeconds(1)).build()).build())
 private var state = "queued"
 private var proposed: Plan? = null
 private var decision: Boolean? = null
 private val events = mutableListOf<String>()
 private fun transition(value: String) { state = value; events.add("${Workflow.currentTimeMillis()}: $value") }
 override fun snapshot() = Snapshot(state, proposed, events.toList())
 override fun decide(approved: Boolean) { if (state == "awaiting_approval" && decision == null) decision = approved }
 override fun run(request: Request): String {
  try {
   transition("investigating")
   val context = ops.investigate(request)
   proposed = ops.plan(request, context)
   if (proposed!!.action != "restart_service") { transition("escalated"); return state }
   transition("awaiting_approval")
   val received = Workflow.await(Duration.ofHours(1)) { decision != null }
   if (!received || decision != true) { transition(if(received) "rejected" else "expired"); return state }
   transition("executing")
   ops.execute(request.service, Workflow.getInfo().workflowId + ":restart")
   transition("completed")
  } catch (e: Exception) { transition("failed") }
  return state
 }
}
