package ops

import org.junit.jupiter.api.Test
import kotlin.test.*

class PlannerTest {
    private val context = Context("vpn", "degraded", "evidence:1")
    private fun response(action: String = "restart_service", done: String = "stop") = mapper.writeValueAsString(mapOf(
        "done" to true, "done_reason" to done,
        "message" to mapOf("content" to mapper.writeValueAsString(mapOf("action" to action, "reason" to "Evidence indicates degradation"))),
        "prompt_eval_count" to 30, "eval_count" to 12,
    ))

    @Test fun localModelProposesWithoutCredentials() {
        val planner = Planner(mapOf("LLM_PROVIDER" to "ollama", "PORTKEY_API_KEY" to "must-not-leak")) { request ->
            assertEquals("127.0.0.1", request.uri().host)
            assertFalse(request.headers().firstValue("x-portkey-api-key").isPresent)
            200 to response()
        }
        val plan = planner.propose(Request("vpn", "VPN failed"), context)
        assertEquals("restart_service", plan.action)
        assertEquals("evidence:1", plan.evidence)
        assertEquals(42, plan.tokens)
    }

    @Test fun invalidOrTruncatedResponsesFailClosed() {
        for (body in listOf(response("export_credentials"), response(done="length"), "not-json")) {
            val planner = Planner(mapOf("LLM_PROVIDER" to "ollama")) { 200 to body }
            assertFails { planner.propose(Request("vpn"), context) }
        }
    }

    @Test fun staleEvidenceNeverCallsModel() {
        val planner = Planner(mapOf("LLM_PROVIDER" to "ollama")) { error("must not call provider") }
        assertEquals("escalate", planner.propose(Request("vpn"), context.copy(ageSeconds=301)).action)
    }

    @Test fun unavailableLocalModelDoesNotPretendToBeDemo() {
        val planner = Planner(mapOf("LLM_PROVIDER" to "ollama")) { 503 to "unavailable" }
        assertFails { planner.propose(Request("vpn"), context) }
    }
}
