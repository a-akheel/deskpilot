package ops

/** Local role tokens are deliberately separate from the downstream integration token. */
data class Config(
    val operatorToken: String,
    val approverToken: String,
    val itToken: String,
    val itUrl: String = "http://127.0.0.1:8091",
    val temporalAddress: String = "127.0.0.1:7233",
    val port: Int = 8080,
) {
    companion object {
        fun fromEnvironment(): Config {
            fun required(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }
                ?: error("$name is required")
            return Config(
                required("OPERATOR_TOKEN"), required("APPROVER_TOKEN"), required("IT_API_TOKEN"),
                System.getenv("IT_API_URL") ?: "http://127.0.0.1:8091",
                System.getenv("TEMPORAL_ADDRESS") ?: "127.0.0.1:7233",
                System.getenv("PORT")?.toInt() ?: 8080,
            ).also { require(it.operatorToken != it.approverToken) { "Use separate role tokens" } }
        }
    }
}
