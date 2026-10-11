package ops

import io.temporal.client.WorkflowClient
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.serviceclient.WorkflowServiceStubsOptions
import io.temporal.worker.WorkerFactory

fun main() {
    val config = Config.fromEnvironment()
    val service = WorkflowServiceStubs.newServiceStubs(
        WorkflowServiceStubsOptions.newBuilder().setTarget(config.temporalAddress).build(),
    )
    val client = WorkflowClient.newInstance(service)
    val factory = WorkerFactory.newInstance(client)
    factory.newWorker("deskpilot").apply {
        registerWorkflowImplementationTypes(IncidentWorkflowImpl::class.java)
        registerActivitiesImplementations(HttpOperations(config))
    }
    factory.start()
    val api = ApiServer(client, config)
    api.start()
    println("DeskPilot API: http://127.0.0.1:${config.port}")
    Runtime.getRuntime().addShutdownHook(Thread {
        api.close()
        factory.shutdown()
        service.shutdown()
    })
}
