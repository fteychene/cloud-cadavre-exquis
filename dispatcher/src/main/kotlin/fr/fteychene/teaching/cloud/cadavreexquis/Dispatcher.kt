package fr.fteychene.teaching.cloud.cadavreexquis

import com.github.jknack.handlebars.Helper
import com.github.jknack.handlebars.Options
import okhttp3.OkHttpClient
import org.http4k.client.JavaHttpClient
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
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit

val INSTANCE_ID = System.getenv("INSTANCE_ID") ?: UUID.randomUUID().toString()
val APP_ID = System.getenv("APP_ID") ?: "DEV_APP_ID"
val PORT = (System.getenv("PORT") ?: "8080").toInt()
val PROVIDER = System.getenv("PROVIDER") ?: "http://localhost:8080"

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

val HealthStatusLens = Body.auto<HealthStatus>().toLens()
val WordResponseLens = Body.auto<WordResponse>().toLens()

object Index : ViewModel {
    override fun template(): String = "index"
}

data class CadavreView(
    val words: List<WordResponse>
) : ViewModel {
    override fun template(): String = "cadavre"
}

val logger: Logger = LoggerFactory.getLogger(INSTANCE_ID);

fun main() {
    logger.info("Starting provider")

    val handlebars = HandlebarsTemplates {
        it.registerHelper("wordBackground", Helper<Int> { context, _ ->
            when (context) {
                0 -> "has-background-primary-light"
                1 -> "has-background-info-light"
                2 -> "has-background-success-light"
                3 -> "has-background-primary-light"
                4 -> "has-background-info-light"
                else -> ""
            }
        })
    }.CachingClasspath()

    val hbsTemplate = Body.viewModel(handlebars, ContentType.TEXT_HTML).toLens()
    val httpClient = OkHttp(client = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(1, TimeUnit.SECONDS)
        .build())

    Runtime.getRuntime().addShutdownHook(object : Thread() {
        override fun run() {
            logger.warn("Stopping provider")
        }
    })
    routes(
        "/" bind Method.GET to {
            Response(Status.PERMANENT_REDIRECT).header("Location", "index.html")
        },
        "/index.html" bind Method.GET to {
            Response(Status.OK).with(hbsTemplate of Index)
        },
        "/health" bind Method.GET to {
            Response(Status.OK).with(HealthStatusLens of HealthStatus(Instant.now()))
        },
        "/cadavreexquis.html" bind Method.GET to {
            val responses = listOf("adjective", "subject", "verb", "adjective", "subject")
                .map {
                    val response = Request(Method.GET, "$PROVIDER/$it")
                        .run(httpClient)
                    response.close()
                    if (response.status == Status.OK) WordResponseLens(response)
                    else {
                        logger.warn("Error calling \"$PROVIDER/$it\" {} - {}", response.status.code, response.bodyString())
                        WordResponse("error", "-", "-")
                    }
                }
            Response(Status.OK).with(hbsTemplate of CadavreView(responses))
        }
    ).asServer(Netty(PORT)).start()

}

