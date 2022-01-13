package fr.fteychene.teaching.cloud.cadavreexquis

import com.github.jknack.handlebars.Helper
import okhttp3.OkHttpClient
import org.http4k.client.DualSyncAsyncHttpHandler
import org.http4k.client.OkHttp
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
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
import java.util.UUID
import java.util.concurrent.TimeUnit

val INSTANCE_ID = System.getenv("INSTANCE_ID") ?: UUID.randomUUID().toString()
val APP_ID = System.getenv("APP_ID") ?: "DEV_APP_ID"
val PORT = (System.getenv("PORT") ?: "8080").toInt()

val SUBJECT_URL: String = System.getenv("SUBJECT_URL")!!
val VERB_URL: String = System.getenv("VERB_URL")!!
val ADJECTIVE_URL: String = System.getenv("ADJECTIVE_URL")!!

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

data class Registration(
    val url: String // http://172.18.0.4:8080
)

val ListRegistrationJsonLens = Body.auto<List<Registration>>().toLens()

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
    val httpClient = OkHttp(
        client = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .build()
    )

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
            val responses = listOf("ADJECTIVE", "SUBJECT", "VERB", "ADJECTIVE", "SUBJECT")
                .map { type ->
                    val provider = retryableChooseProvider(type, httpClient)
                    val response = Request(Method.GET, "${provider.url}/${type.lowercase()}")
                        .run(httpClient)
                    response.close()
                    if (response.status == Status.OK) WordResponseLens(response)
                    else {
                        logger.warn(
                            "Error calling \"$provider/${type.lowercase()}\" {} - {}",
                            response.status.code,
                            response.bodyString()
                        )
                        WordResponse("error", "-", "-")
                    }
                }
            Response(Status.OK).with(hbsTemplate of CadavreView(responses))
        }
    ).asServer(Netty(PORT)).start()
}

private fun chooseProvider(
    type: String,
    httpClient: DualSyncAsyncHttpHandler
): Registration? = when (type) {
        "ADJECTIVE" -> Registration(ADJECTIVE_URL)
        "SUBJECT" -> Registration(SUBJECT_URL)
        "VERB" -> Registration(VERB_URL)
        else -> null
    }

fun retryableChooseProvider(
    type: String,
    httpClient: DualSyncAsyncHttpHandler,
    retries: Int = 0
): Registration =
    chooseProvider(type, httpClient) ?: run {
        if (retries > 4) {
            Thread.sleep(300)
            retryableChooseProvider(type, httpClient, retries + 1)
        } else throw IllegalStateException("No providers could be loaded for $type")
    }


