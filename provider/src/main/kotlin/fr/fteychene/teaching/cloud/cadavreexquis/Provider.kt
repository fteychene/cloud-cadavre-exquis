package fr.fteychene.teaching.cloud.cadavreexquis

import org.http4k.core.*
import org.http4k.format.Jackson.auto
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Netty
import org.http4k.server.asServer
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.ViewModel
import org.http4k.template.viewModel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*

val INSTANCE_ID = System.getenv("INSTANCE_ID") ?: UUID.randomUUID().toString()
val APP_ID = System.getenv("APP_ID") ?: "DEV_APP_ID"
val PORT = (System.getenv("PORT") ?: "8080").toInt()

data class HealthStatus(
    val time: Instant,
    val appId: String = APP_ID,
    val instanceId: String = INSTANCE_ID
)

data class WordResponse(
    val value: String,
    val appId: String = APP_ID,
    val instanceId: String = INSTANCE_ID
)

val values = mapOf(
    WordType.SUBJECT to listOf(
        "dead body",
        "elephant",
        "go language",
        "laptop",
        "container",
        "micro-service",
        "turtle",
        "whale"
    ),
    WordType.VERB to listOf(
        "will drink",
        "smashed",
        "smokes",
        "eats",
        "walks towards",
        "loves",
        "helps",
        "pushes",
        "debugs"
    ),
    WordType.ADJECTIVE to listOf(
        "the exquisite",
        "a pink",
        "the rotten",
        "a red",
        "the floating",
        "a broken",
        "a shiny",
        "the pretty",
        "the impressive",
        "an awesome"
    ),
)

fun random(type: WordType): String =
    values[type]?.random()!!

enum class WordType {
    SUBJECT, VERB, ADJECTIVE
}

val HealthStatusLens = Body.auto<HealthStatus>().toLens()
val WordResponseLens = Body.auto<WordResponse>().toLens()
val HbsTemplate = Body.viewModel(HandlebarsTemplates().CachingClasspath(), ContentType.TEXT_HTML).toLens()

object Index : ViewModel {
    override fun template(): String = "index"
}

val logger: Logger = LoggerFactory.getLogger(INSTANCE_ID);

fun main() {
    logger.info("Starting provider")
    Runtime.getRuntime().addShutdownHook(object: Thread() {
        override fun run() {
            logger.warn("Stopping provider")
        }
    })
    routes(
        "/index.html" bind Method.GET to {
            Response(Status.OK).with(HbsTemplate of Index)
        },
        "/health" bind Method.GET to {
            Response(Status.OK).with(HealthStatusLens of HealthStatus(Instant.now()))
        },
        "/verb" bind Method.GET to {
            Response(Status.OK).with(WordResponseLens of WordResponse(random(WordType.VERB)))
        },
        "/subject" bind Method.GET to {
            Response(Status.OK).with(WordResponseLens of WordResponse(random(WordType.SUBJECT)))
        },
        "/adjective" bind Method.GET to {
            Response(Status.OK).with(WordResponseLens of WordResponse(random(WordType.ADJECTIVE)))
        }
    ).asServer(Netty(PORT)).start()

}