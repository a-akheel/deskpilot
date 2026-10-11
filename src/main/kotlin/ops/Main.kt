package ops

import com.sun.net.httpserver.HttpServer
import io.temporal.client.*
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.worker.WorkerFactory
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.Executors

fun main() {
 val client = WorkflowClient.newInstance(WorkflowServiceStubs.newLocalServiceStubs())
 val factory = WorkerFactory.newInstance(client)
 factory.newWorker("it-operations").apply {
  registerWorkflowImplementationTypes(IncidentWorkflowImpl::class.java)
  registerActivitiesImplementations(HttpOperations())
 }
 factory.start()
 val operatorToken = System.getenv("OPERATOR_TOKEN") ?: error("OPERATOR_TOKEN required")
 val approvalToken = System.getenv("APPROVER_TOKEN") ?: error("APPROVER_TOKEN required")
 require(operatorToken != approvalToken) { "Use separate operator and approver credentials" }
 val server = HttpServer.create(InetSocketAddress("127.0.0.1",8080),0)
 server.executor = Executors.newFixedThreadPool(8)
 server.createContext("/api/") { exchange ->
  fun respond(code: Int, body: Any) {
   val bytes = mapper.writeValueAsBytes(body)
   exchange.responseHeaders.add("Content-Type","application/json")
   exchange.sendResponseHeaders(code, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
  }
  try {
   val path = exchange.requestURI.path.removePrefix("/api/").split("/")
   val auth = exchange.requestHeaders.getFirst("Authorization")
   val approval = path.size == 3 && path[2] == "decision"
   val expected = if(approval) approvalToken else operatorToken
   if(auth != "Bearer $expected") { respond(403,mapOf("error" to "Missing or insufficient credentials")) }
   else if(path == listOf("incidents") && exchange.requestMethod == "POST") {
    val bytes = exchange.requestBody.readNBytes(8193); require(bytes.size <= 8192)
    val request = mapper.readValue(bytes, Request::class.java)
    require(request.service in setOf("vpn","email","helpdesk") && request.description.isNotBlank() && request.description.length <= 4000)
    val id = UUID.randomUUID().toString()
    val workflow = client.newWorkflowStub(IncidentWorkflow::class.java, WorkflowOptions.newBuilder().setWorkflowId(id).setTaskQueue("it-operations").build())
    WorkflowClient.start(workflow::run,request); respond(202,mapOf("id" to id))
   } else if(path.size >= 2 && path[0] == "incidents") {
    UUID.fromString(path[1])
    val workflow = client.newWorkflowStub(IncidentWorkflow::class.java,path[1])
    if(approval && exchange.requestMethod == "POST") {
     val bytes = exchange.requestBody.readNBytes(1025); require(bytes.size <= 1024)
     val body = mapper.readTree(bytes); require(body.path("approved").isBoolean)
     workflow.decide(body.path("approved").asBoolean()); respond(202,mapOf("accepted" to true))
    } else if(path.size == 2 && exchange.requestMethod == "GET") respond(200,workflow.snapshot())
    else respond(404,mapOf("error" to "Unknown route"))
   } else respond(404,mapOf("error" to "Unknown route"))
  } catch(e: IllegalArgumentException) { respond(400,mapOf("error" to "Invalid request")) }
    catch(e: Exception) { respond(503,mapOf("error" to "Workflow unavailable")) }
 }
 server.start(); println("Operations API listening at http://127.0.0.1:8080")
 Runtime.getRuntime().addShutdownHook(Thread { server.stop(0); factory.shutdown() })
}
