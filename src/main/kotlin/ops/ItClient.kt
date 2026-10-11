package ops

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.temporal.failure.ApplicationFailure
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

val mapper = jacksonObjectMapper()

class ItClient(private val config: Config) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()

    private fun call(path: String, key: String? = null): String {
        val builder = HttpRequest.newBuilder(URI.create(config.itUrl + path))
            .timeout(Duration.ofSeconds(5))
            .header("Authorization", "Bearer ${config.itToken}")
        if (key != null) {
            builder.header("Idempotency-Key", key).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
        }
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() in 400..499 && response.statusCode() !in setOf(408, 429)) {
            throw ApplicationFailure.newNonRetryableFailure("IT request denied", "IT_HTTP_${response.statusCode()}")
        }
        check(response.statusCode() in 200..299) { "IT API returned ${response.statusCode()}" }
        return response.body()
    }

    fun context(service: String): Context {
        require(service in allowedServices)
        val context = try {
            mapper.readValue<Context>(call("/services/$service"))
        } catch (e: com.fasterxml.jackson.core.JacksonException) {
            throw ApplicationFailure.newNonRetryableFailure("Invalid operational context", "INVALID_CONTEXT")
        }
        if (context.service != service || context.status !in setOf("healthy", "degraded") || context.evidence.isBlank()) {
            throw ApplicationFailure.newNonRetryableFailure("Context schema mismatch", "INVALID_CONTEXT")
        }
        return context
    }

    fun restart(service: String, key: String): String {
        require(service in allowedServices)
        return call("/services/$service/restart", key)
    }
}
