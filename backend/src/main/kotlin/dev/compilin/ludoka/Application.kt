package dev.compilin.ludoka

import dev.compilin.ludoka.endpoints.configureAuthEndpoint
import dev.compilin.ludoka.endpoints.configureGamesEndpoint
import dev.compilin.ludoka.endpoints.configureLibrariesEndpoint
import dev.compilin.ludoka.endpoints.configureUsersEndpoint
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.event.Level

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {

    val db = AppDatabase(environment)

    configureSecurity(db)

    install(DefaultHeaders) {
        header("X-Engine", "Ktor") // will send this header with each response
    }
    if (environment.config.propertyOrNull("ludoka.reverse_proxy")?.getString() == "true") {
        install(ForwardedHeaders) // WARNING: for security, do not include this if not behind a reverse proxy
        install(XForwardedHeaders) // WARNING: for security, do not include this if not behind a reverse proxy
    }

    install(CallLogging) {
        level = Level.INFO
    }

    install(ContentNegotiation) {
        json()
    }
    install(AutoHeadResponse)
    install(Resources)
    install(StatusPages) {
        unhandled {
            it.respond(HttpStatusCode.InternalServerError)
        }
    }

    routing {
        configureAuthEndpoint(db)
        configureUsersEndpoint(db)
        configureGamesEndpoint(db)
        configureLibrariesEndpoint(db)

        openAPI(path = "openapi")
    }
}
