
val main = "fr.fteychene.teaching.cloud.cadavreexquis.DispatcherKt"
project.setProperty("mainClassName", main)

dependencies {
    implementation(kotlin("stdlib"))
    implementation(platform("org.http4k:http4k-bom:4.12+"))
    implementation("org.http4k:http4k-core")
    implementation("org.http4k:http4k-server-netty")
    implementation("org.http4k:http4k-format-jackson")
    implementation("org.http4k:http4k-client-okhttp")
    implementation("org.http4k:http4k-client-websocket")
    implementation("org.http4k:http4k-template-handlebars")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.11+")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")
    implementation("io.github.microutils:kotlin-logging-jvm:2.0.2")
    implementation("org.slf4j:slf4j-api:1.7.25")
    implementation("ch.qos.logback:logback-classic:1.2.3")
}


jib  {
    from {
        image = "openjdk:slim-buster"
    }
    to {
        image = "fteychene/cloud-cadavre-exquis-${project.name}-bis"
        tags = setOf("${project.version}", "latest")
    }
    container {
        mainClass = main
        ports = listOf("8080")
        format = com.google.cloud.tools.jib.api.buildplan.ImageFormat.OCI
    }
}