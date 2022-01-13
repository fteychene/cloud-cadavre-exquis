package fr.fteychene.teaching.cloud.cadavreexquis

import okhttp3.OkHttpClient
import org.http4k.client.OkHttp
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
import java.net.InetAddress
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit

val INSTANCE_ID = System.getenv("INSTANCE_ID") ?: UUID.randomUUID().toString()
val APP_ID = System.getenv("APP_ID") ?: "DEV_APP_ID"
val PORT = (System.getenv("PORT") ?: "8080").toInt()

val PROVIDER_TYPE: WordType = System.getenv("WORD_TYPE")?.let { conf -> WordType.values().firstOrNull { it.name == conf } } ?: WordType.values().toList().shuffled().first()

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

data class Registration(
    val url: String, // http://172.18.0.4:8080
    val type: WordType, // VERB,
    val healthcheck: String
)

val RegistrationJsonLens = Body.auto<Registration>().toLens()

fun main() {
    logger.info("Starting provider of $PROVIDER_TYPE")
    Runtime.getRuntime().addShutdownHook(object: Thread() {
        override fun run() {
            logger.warn("Stopping provider")
        }
    })

    logger.info("Register provider on register")

    routes(
        "/index.html" bind Method.GET to {
            Response(Status.OK).with(HbsTemplate of Index)
        },
        "/health" bind Method.GET to {
            Response(Status.OK).with(HealthStatusLens of HealthStatus(Instant.now()))
        },
        "/verb" bind Method.GET to {
            if (PROVIDER_TYPE == WordType.VERB) Response(Status.OK).with(WordResponseLens of WordResponse(random(WordType.VERB)))
            else Response(Status.BAD_GATEWAY)
        },
        "/subject" bind Method.GET to {
            if (PROVIDER_TYPE == WordType.SUBJECT) Response(Status.OK).with(WordResponseLens of WordResponse(random(WordType.SUBJECT)))
            else Response(Status.BAD_GATEWAY)
            Response(Status.OK).with(WordResponseLens of WordResponse(random(WordType.SUBJECT)))
        },
        "/adjective" bind Method.GET to {
            if (PROVIDER_TYPE == WordType.ADJECTIVE) Response(Status.OK).with(WordResponseLens of WordResponse(random(WordType.ADJECTIVE)))
            else Response(Status.BAD_GATEWAY)
        }
    ).asServer(Netty(PORT)).start()

}