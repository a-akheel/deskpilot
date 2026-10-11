package ops

import io.temporal.client.*
import io.temporal.testing.TestWorkflowEnvironment
import io.temporal.worker.WorkerFactory
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.test.*

class WorkflowTest {
    private class Fixture : Operations {
        var context = Context("vpn", "degraded", "monitor:vpn", revision = "v1")
        var attempts = 0
        var effects = 0
        var failExecution = false
        var loseFirstResponse = false
        val committedKeys = mutableSetOf<String>()
        override fun investigate(request: Request) = context
        override fun plan(request: Request, context: Context) = Plan("restart_service", "Test", context.evidence)
        override fun execute(service: String, key: String): String {
            attempts++
            if (failExecution) error("Transient adapter failure")
            if (committedKeys.add(key)) effects++
            if (loseFirstResponse && attempts == 1) error("Response lost after commit")
            return "ok"
        }
    }

    private fun withWorkflow(block: (TestWorkflowEnvironment, IncidentWorkflow, Fixture) -> Unit) {
        val env = TestWorkflowEnvironment.newInstance()
        val fixture = Fixture()
        env.newWorker("test").apply {
            registerWorkflowImplementationTypes(IncidentWorkflowImpl::class.java)
            registerActivitiesImplementations(fixture)
        }
        env.start()
        try {
            val stub = env.workflowClient.newWorkflowStub(IncidentWorkflow::class.java,
                WorkflowOptions.newBuilder().setTaskQueue("test").build())
            WorkflowClient.start(stub::run, Request("vpn", "VPN unavailable"))
            val deadline = System.nanoTime() + 10_000_000_000
            while (stub.snapshot().status != "awaiting_approval") {
                check(System.nanoTime() < deadline)
                Thread.sleep(10)
            }
            block(env, stub, fixture)
        } finally { env.close() }
    }
    private fun result(stub: IncidentWorkflow) = WorkflowStub.fromTyped(stub).getResult(30, TimeUnit.SECONDS, String::class.java)
    private fun approve(stub: IncidentWorkflow) = stub.decide(true, stub.snapshot().plan!!.id)

    @Test fun `approval permits one action`() = withWorkflow { _, stub, fixture ->
        approve(stub)
        assertEquals("completed", result(stub))
        assertEquals(1, fixture.effects)
    }
    @Test fun `rejection never executes`() = withWorkflow { _, stub, fixture ->
        stub.decide(false, stub.snapshot().plan!!.id)
        assertEquals("rejected", result(stub))
        assertEquals(0, fixture.effects)
    }
    @Test fun `expiry never executes`() = withWorkflow { _, stub, fixture ->
        assertEquals("expired", result(stub))
        assertEquals(0, fixture.effects)
    }
    @Test fun `wrong plan approval is ignored`() = withWorkflow { _, stub, fixture ->
        stub.decide(true, "different-plan")
        assertEquals("expired", result(stub))
        assertEquals(0, fixture.effects)
    }
    @Test fun `duplicate decisions do not duplicate execution`() = withWorkflow { _, stub, fixture ->
        val planId = stub.snapshot().plan!!.id
        stub.decide(true, planId)
        stub.decide(true, planId)
        assertEquals("completed", result(stub))
        assertEquals(1, fixture.effects)
    }
    @Test fun `changed evidence invalidates approval`() = withWorkflow { _, stub, fixture ->
        fixture.context = fixture.context.copy(revision = "v2")
        approve(stub)
        assertEquals("stale_approval", result(stub))
        assertEquals(0, fixture.effects)
    }
    @Test fun `aged evidence invalidates approval`() = withWorkflow { _, stub, fixture ->
        fixture.context = fixture.context.copy(ageSeconds = 7200)
        approve(stub)
        assertEquals("stale_approval", result(stub))
        assertEquals(0, fixture.effects)
    }
    @Test fun `retries stop after three failures`() = withWorkflow { _, stub, fixture ->
        fixture.failExecution = true
        approve(stub)
        assertEquals("failed", result(stub))
        assertEquals(3, fixture.attempts)
        assertEquals(0, fixture.effects)
    }
    @Test fun `response lost after action commit does not duplicate effect`() = withWorkflow { _, stub, fixture ->
        fixture.loseFirstResponse = true
        approve(stub)
        assertEquals("completed", result(stub))
        assertEquals(2, fixture.attempts)
        assertEquals(1, fixture.effects)
    }
    @Test fun `completed history replays without nondeterminism`() = withWorkflow { env, stub, _ ->
        approve(stub)
        assertEquals("completed", result(stub))
        val execution = WorkflowStub.fromTyped(stub).execution
        io.temporal.testing.WorkflowReplayer.replayWorkflowExecution(
            env.getWorkflowExecutionHistory(execution), IncidentWorkflowImpl::class.java,
        )
    }
}
