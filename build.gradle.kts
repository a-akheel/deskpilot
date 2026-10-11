plugins { kotlin("jvm") version "2.1.20"; application }
repositories { mavenCentral() }
dependencies {
 implementation("io.temporal:temporal-sdk:1.29.0")
 implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3")
 implementation("org.slf4j:slf4j-simple:2.0.17")
 testImplementation(kotlin("test"))
 testImplementation("io.temporal:temporal-testing:1.29.0")
}
kotlin { jvmToolchain(21) }
application { mainClass.set("ops.MainKt") }
tasks.test { useJUnitPlatform() }
