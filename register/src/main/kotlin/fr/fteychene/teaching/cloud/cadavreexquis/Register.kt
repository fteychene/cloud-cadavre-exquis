package fr.fteychene.teaching.cloud.cadavreexquis

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import org.http4k.client.OkHttp
import org.http4k.core.*
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
val PORT = (System.getenv("PORT") ?: "8080").toInt()
val STORAGE = (System.getenv("STORAGE") ?: "PG").toStorage()

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

fun main() {
    logger.info("Starting register")
    Runtime.getRuntime().addShutdownHook(object : Thread() {
        override fun run() {
            logger.warn("Stopping register")
        }
    })

    val httpClient = OkHttp(
            client = OkHttpClient.Builder()
                    .connectTimeout(2, TimeUnit.SECONDS)
                    .readTimeout(2, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build())

    if (STORAGE is StorageType.Database) {
        createTables(STORAGE.connection)
    }

    // Health checking
    GlobalScope.launch {
        while (true) {
            logger.info("Health check triggered")
            delay(3000L)
            STORAGE.loadRegistrations(null).forEach { provider ->
                try {
                    val response = Request(Method.GET, "${provider.url}${provider.healthcheck ?: "/health"}")
                            .run(httpClient)
                    if (response.status != Status.OK) {
                        logger.info("Delete $provider. Health check is not responding 200")
                        STORAGE.deleteRegistration(provider)
                    }
                } catch (e: SocketException) {
                    logger.info("Delete $provider. Connectivity error", e)
                    STORAGE.deleteRegistration(provider)
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
                    logger.info("Registering a {} provider at {}", body.type, body.url)
                    STORAGE.saveRegistration(body)
                    Response(Status.CREATED).with(RegistrationJsonLens of body)
                }
            },
            "/providers" bind Method.GET to { request ->
                val typeParam: Type = Query.enum<Type>().required("type")(request)
                val providers = STORAGE.loadRegistrations(typeParam)
                Response(Status.OK).with(RegistrationListJsonLens of providers.map { it.toResponse() })
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

val formatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(Locale.FRANCE)
        .withZone(ZoneId.systemDefault())

fun saveEvent(connection: () -> Connection, event: Event) =
        execute(connection) {
            val stmt = it.prepareStatement("INSERT INTO events(id, event, event_time, url, owner, healthcheck) VALUES (?, ?, ?, ?, ?, ?)")
            stmt.setObject(1, UUID.randomUUID())
            stmt.setString(2, event.type.name)
            stmt.setString(3, formatter.format(LocalDateTime.now()))
            stmt.setString(4, event.registration.url)
            stmt.setString(5, event.registration.name)
            stmt.setString(6, event.registration.healthcheck)
            stmt.executeUpdate()
        }

fun createTables(connection: () -> Connection) =
        execute(connection) {
            it.createStatement()
                    .executeUpdate("CREATE TABLE IF NOT EXISTS registrations (url VARCHAR, type VARCHAR, name VARCHAR, healthcheck VARCHAR, PRIMARY KEY (url, type))")
            it.createStatement()
                    .executeUpdate("CREATE TABLE IF NOT EXISTS events (id UUID, event VARCHAR, event_time VARCHAR, url VARCHAR, owner VARCHAR, healthcheck VARCHAR)")
        }


suspend fun StorageType.saveRegistration(registration: Registration) = when (this) {
    is StorageType.Memory -> mutex.withLock {
        state.add(registration)
    }

    is StorageType.Database -> {
        execute(connection) {
            val stmt = it.prepareStatement("INSERT INTO registrations(url, type, name, healthcheck) VALUES (?, ?, ?, ?)")
            stmt.setString(1, registration.url)
            stmt.setString(2, registration.type.name)
            stmt.setString(3, registration.name)
            stmt.setString(4, registration.healthcheck)
            stmt.executeUpdate()
        }
        saveEvent(connection, (Event(EventType.REGISTER, registration)))
    }
}

suspend fun StorageType.deleteRegistration(registration: Registration) = when (this) {
    is StorageType.Memory -> mutex.withLock {
        state.remove(registration)
    }
    is StorageType.Database -> {
        execute(connection) {
            val stmt = it.prepareStatement("DELETE FROM registrations WHERE url = ? AND type = ?")
            stmt.setString(1, registration.url)
            stmt.setString(2, registration.type.name)
            stmt.execute()
        }
        saveEvent(connection, (Event(EventType.UNHEALTHY, registration)))
    }
}

fun StorageType.loadRegistrations(type: Type?): List<Registration> =
        when (this) {
            is StorageType.Memory -> state.filter { type == null || type == it.type }
            is StorageType.Database -> execute(connection) {
                val stmt = it.prepareStatement("SELECT url, type, name, healthcheck FROM registrations ${type?.let { "WHERE type = ?" } ?: ""}")
                if (type != null) stmt.setString(1, type.name)
                stmt.executeQuery().use {
                    generateSequence {
                        if (it.next()) Registration(it.getString(1), Type.valueOf(it.getString(2)), it.getString(3), it.getString(4))
                        else null
                    }.toList()  // must be inside the use() block
                }
            }
        }

sealed class StorageType {
    data class Database(val connection: () -> Connection) : StorageType()
    data class Memory(val mutex: Mutex, val state: MutableList<Registration>) : StorageType()
}

fun String.toStorage(): StorageType =
        when (this) {
            "PG" -> StorageType.Database(postgres())
            "MEMORY" -> StorageType.Memory(Mutex(), mutableListOf())
            else -> throw RuntimeException("Invalid configuration : $this is not a valid sotrage type")
        }
