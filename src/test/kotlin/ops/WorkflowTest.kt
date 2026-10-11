package ops
import io.temporal.testing.TestWorkflowEnvironment
import io.temporal.client.*
import org.junit.jupiter.api.Test
import kotlin.test.*
class WorkflowTest {
 private fun scenario(approve: Boolean?, action: String = "restart_service"): Pair<String,Int> {
  val env=TestWorkflowEnvironment.newInstance()
  var calls=0
  val worker=env.newWorker("test")
  worker.registerWorkflowImplementationTypes(IncidentWorkflowImpl::class.java)
  worker.registerActivitiesImplementations(object: Operations {
   override fun investigate(request: Request)=Context(request.service,"degraded","test:monitor")
   override fun plan(request: Request,context: Context)=Plan(action,"Test plan",context.evidence)
   override fun execute(service: String,key: String): String { calls++; return "ok" }
  })
  env.start()
  try {
   val stub=env.workflowClient.newWorkflowStub(IncidentWorkflow::class.java,WorkflowOptions.newBuilder().setTaskQueue("test").build())
   WorkflowClient.start(stub::run,Request("vpn","VPN unavailable"))
   if(approve!=null && action=="restart_service") {
    val deadline=System.nanoTime()+10_000_000_000
    while(stub.snapshot().status!="awaiting_approval") { check(System.nanoTime()<deadline); Thread.sleep(10) }
    stub.decide(approve)
   }
   return WorkflowStub.fromTyped(stub).getResult(String::class.java) to calls
  } finally { env.close() }
 }
 @Test fun `approval permits exactly one action`() { assertEquals("completed" to 1,scenario(true)) }
 @Test fun `rejection never executes`() { assertEquals("rejected" to 0,scenario(false)) }
 @Test fun `expiry never executes`() { assertEquals("expired" to 0,scenario(null)) }
 @Test fun `ambiguous plans escalate`() { assertEquals("escalated" to 0,scenario(null,"escalate")) }
}
