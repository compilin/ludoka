package dev.compilin.ludoka.endpoints

import dev.compilin.ludoka.AUTH_ADMIN
import dev.compilin.ludoka.AUTH_SESSION
import dev.compilin.ludoka.AppDatabase
import dev.compilin.ludoka.model.Game
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.Routing

@Suppress("unused")
@Resource("/games")
private class Games {
    @Resource("{id}")
    class Id(val parent: Games, val id: Int)
}


fun Routing.configureGamesEndpoint(db: AppDatabase) {
    authenticate(AUTH_ADMIN) {
        // Create game
        post<Games> {
            val game = call.receive<Game>()
            val id = db.games.create(game)
            call.respond(HttpStatusCode.Created, id)
        }

        // Update game
        patch<Games.Id> {
            val game = call.receive<Game>()
            db.games.update(it.id, game)
            call.respond(HttpStatusCode.OK)
        }

        // Delete game
        delete<Games.Id> {
            db.games.delete(it.id)
            call.respond(HttpStatusCode.OK)
        }
    }

    authenticate(AUTH_SESSION, AUTH_ADMIN) {
        // Get game list
        get<Games> {
            val games = db.games.readAll()
            call.respond(HttpStatusCode.OK, games)
        }
        // Read game
        get<Games.Id> {
            val game = db.games.read(it.id)
            if (game != null) {
                call.respond(HttpStatusCode.OK, game)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}
