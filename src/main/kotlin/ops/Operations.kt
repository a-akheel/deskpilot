package ops

import io.temporal.activity.ActivityInterface

@ActivityInterface
interface Operations {
    fun investigate(request: Request): Context
    fun plan(request: Request, context: Context): Plan
    fun execute(service: String, key: String): String
}

class HttpOperations(config: Config) : Operations {
    private val it = ItClient(config)
    private val planner = Planner()

    override fun investigate(request: Request) = it.context(request.service)
    override fun plan(request: Request, context: Context) = planner.propose(request, context)
    override fun execute(service: String, key: String) = it.restart(service, key)
}
