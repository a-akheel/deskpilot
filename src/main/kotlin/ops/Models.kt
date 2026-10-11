package ops

data class Request(val service: String = "", val description: String = "")

data class Context(
    val service: String = "",
    val status: String = "",
    val evidence: String = "",
    val ageSeconds: Long = 0,
    val revision: String = "v1",
    val note: String = "",
)

data class Plan(
    val action: String = "escalate",
    val reason: String = "",
    val evidence: String = "",
    val tokens: Int = 0,
    val latencyMs: Long = 0,
    val id: String = "",
)

data class Snapshot(
    val status: String = "queued",
    val plan: Plan? = null,
    val events: List<String> = emptyList(),
    val failure: String? = null,
)

val allowedServices = setOf("vpn", "email", "helpdesk")
fun Context.permitsRestart() = status == "degraded" && ageSeconds in 0..300 && evidence.isNotBlank()
