package ops

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.temporal.client.*
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.Executors

class ApiServer(private val client: WorkflowClient, private val config: Config) : AutoCloseable {
    private val pool = Executors.newFixedThreadPool(8)
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", config.port), 0)

    init {
        server.executor = pool
        server.createContext("/api/") { exchange -> handle(exchange) }
    }
    fun start() = server.start()
    override fun close() { server.stop(0); pool.shutdown() }

    private fun respond(exchange: HttpExchange, code: Int, body: Any) {
        val bytes = mapper.writeValueAsBytes(body)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
    private fun readBody(exchange: HttpExchange, limit: Int): ByteArray {
        val bytes = exchange.requestBody.readNBytes(limit + 1)
        require(bytes.size <= limit) { "Request too large" }
        return bytes
    }
    private fun handle(exchange: HttpExchange) {
        try {
            val path = exchange.requestURI.path.removePrefix("/api/").split("/")
            val approval = path.size == 3 && path[2] == "decision"
            val expected = if (approval) config.approverToken else config.operatorToken
            if (exchange.requestHeaders.getFirst("Authorization") != "Bearer $expected") {
                respond(exchange, 403, mapOf("error" to "Missing or insufficient credentials"))
                return
            }
            if (path == listOf("health") && exchange.requestMethod == "GET") {
                respond(exchange, 200, mapOf("service" to "DeskPilot", "apiVersion" to 2))
            } else if (path == listOf("incidents") && exchange.requestMethod == "POST") {
                val request = mapper.readValue(readBody(exchange, 8192), Request::class.java)
                require((request.service.isBlank() || request.service in allowedServices) &&
                    request.description.isNotBlank() && request.description.length <= 4000)
                val id = UUID.randomUUID().toString()
                val workflow = client.newWorkflowStub(IncidentWorkflow::class.java,
                    WorkflowOptions.newBuilder().setWorkflowId(id).setTaskQueue("deskpilot").build())
                WorkflowClient.start(workflow::run, request)
                respond(exchange, 202, mapOf("id" to id))
            } else if (path.size >= 2 && path[0] == "incidents") {
                UUID.fromString(path[1])
                val workflow = client.newWorkflowStub(IncidentWorkflow::class.java, path[1])
                if (approval && exchange.requestMethod == "POST") {
                    val body = mapper.readTree(readBody(exchange, 1024))
                    require(body.path("approved").isBoolean && body.path("planId").isTextual)
                    workflow.decide(body.path("approved").asBoolean(), body.path("planId").asText())
                    respond(exchange, 202, mapOf("submitted" to true))
                } else if (path.size == 2 && exchange.requestMethod == "GET") {
                    respond(exchange, 200, workflow.snapshot())
                } else respond(exchange, 404, mapOf("error" to "Unknown route"))
            } else respond(exchange, 404, mapOf("error" to "Unknown route"))
        } catch (e: IllegalArgumentException) {
            respond(exchange, 400, mapOf("error" to "Invalid request"))
        } catch (e: com.fasterxml.jackson.core.JacksonException) {
            respond(exchange, 400, mapOf("error" to "Invalid JSON"))
        } catch (e: WorkflowNotFoundException) {
            respond(exchange, 404, mapOf("error" to "Incident not found"))
        } catch (e: Exception) {
            respond(exchange, 503, mapOf("error" to "Workflow unavailable"))
        }
    }
}
