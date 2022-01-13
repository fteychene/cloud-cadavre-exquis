package fr.fteychene.teaching.cloud.cadavreexquis

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.internal.toImmutableList
import org.http4k.client.OkHttp
import org.http4k.core.*
import org.http4k.core.ContentType
import org.http4k.format.Jackson.auto
import org.http4k.lens.Query
import org.http4k.lens.enum
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Netty
import org.http4k.server.asServer
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.ViewModel
import org.http4k.template.viewModel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.SocketException
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
import java.util.concurrent.TimeUnit

val INSTANCE_ID = System.getenv("INSTANCE_ID") ?: UUID.randomUUID().toString()
val APP_ID = System.getenv("APP_ID") ?: "DEV_APP_ID"
val PORT = (System.getenv("PORT") ?: "8080").toInt()

val mutex = Mutex()
val state: MutableList<Registration> = mutableListOf()

val HbsTemplate = Body.viewModel(HandlebarsTemplates().CachingClasspath(), ContentType.TEXT_HTML).toLens()
val logger: Logger = LoggerFactory.getLogger(INSTANCE_ID)

data class Index(
    val time: Instant = Instant.now()
) : ViewModel {
    override fun template(): String = "index"
}

enum class Type {
    VERB, ADJECTIVE, SUBJECT
}

data class Registration(
    val url: String, // http://172.18.0.4:8080
    val type: Type, // VERB
    val name: String?,
    val healthcheck: String?
)

data class RegistrationResponse(
    val url: String, // http://172.18.0.4:8080
    val type: Type, // VERB
)

fun Registration.toResponse(): RegistrationResponse = RegistrationResponse(url, type)

val RegistrationJsonLens = Body.auto<Registration>().toLens()

val RegistrationListJsonLens = Body.auto<List<RegistrationResponse>>().toLens()

val saveToPostgres: (Event) -> Unit = {
    save(postgres(), it)
}

fun main() {
    logger.info("Starting register")
    Runtime.getRuntime().addShutdownHook(object : Thread() {
        override fun run() {
            logger.warn("Stopping register")
        }
    })

    val httpClient = OkHttp(
        client = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .build()
    )

    // Health checking
    GlobalScope.launch {
        while (true) {
            logger.info("Health check triggered")
            delay(3000L)
            mutex.withLock {
                state.toImmutableList().forEach { provider ->
                    try {
                        val response = Request(Method.GET, "${provider.url}${provider.healthcheck ?: "/health"}")
                            .run(httpClient)
                        if (response.status != Status.OK) {
                            logger.info("Delete $provider")
                            state.remove(provider)
                            saveToPostgres(Event(EventType.UNHEALTHY, provider))
                        }
                    } catch (e: SocketException) {
                        logger.info("Delete $provider")
                        state.remove(provider)
                    }
                }
            }
        }
    }

    routes(
        "/index.html" bind Method.GET to {
            Response(Status.OK).with(HbsTemplate of Index())
        },
        "/providers" bind Method.PUT to { request ->
            val body: Registration = RegistrationJsonLens(request)
            runBlocking {
                mutex.withLock {
                    state.add(body)
                    saveToPostgres(Event(EventType.REGISTER, body))
                }
                Response(Status.CREATED).with(RegistrationJsonLens of body)
            }
        },
        "/providers" bind Method.GET to { request ->
            val typeParam: Type = Query.enum<Type>().required("type")(request)
            Response(Status.OK).with(
                RegistrationListJsonLens of state.toImmutableList().map { it.toResponse() }.filter { it.type == typeParam })
        }
    ).asServer(Netty(PORT)).start()

}

data class Event(val type: EventType, val registration: Registration)

enum class EventType {
    REGISTER, UNHEALTHY
}

fun postgres(): () -> Connection = {
    DriverManager.getConnection(
        "jdbc:postgresql://${System.getenv("POSTGRESQL_ADDON_HOST")!!}:${System.getenv("POSTGRESQL_ADDON_PORT")!!}/${
            System.getenv(
                "POSTGRESQL_ADDON_DB"
            )!!
        }",
        System.getenv("POSTGRESQL_ADDON_USER")!!,
        System.getenv("POSTGRESQL_ADDON_PASSWORD")!!
    )
}

fun <R> execute(provider: () -> Connection, block: (Connection) -> R): R =
    provider().use(block)

val formatter = DateTimeFormatter.ofLocalizedDateTime( FormatStyle.FULL )
    .withLocale( Locale.FRANCE )
    .withZone( ZoneId.systemDefault() );

fun save(connection: () -> Connection, event: Event) =
    execute(connection) {
        it.createStatement()
            .executeUpdate("CREATE TABLE IF NOT EXISTS events (id UUID, event VARCHAR, event_time VARCHAR, url VARCHAR, owner VARCHAR, healthcheck VARCHAR )")
        val stmt = it.prepareStatement("INSERT INTO events VALUES (?, ?, ?, ?, ?, ?)")
        stmt.setObject(1, UUID.randomUUID())
        stmt.setString(2, event.type.name)
        stmt.setString(3, formatter.format(LocalDateTime.now()))
        stmt.setString(4, event.registration.url)
        stmt.setString(5, event.registration.name)
        stmt.setString(6, event.registration.healthcheck)
        stmt.executeUpdate()
    }

