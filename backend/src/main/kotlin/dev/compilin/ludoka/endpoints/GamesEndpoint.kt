package dev.compilin.ludoka.endpoints

import dev.compilin.ludoka.AUTH_ADMIN
import dev.compilin.ludoka.AUTH_SESSION
import dev.compilin.ludoka.ApiBase
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
private data class Games(val parent: ApiBase) {
    @Resource("{id}")
    class Id(val parent: Games, val id: Int)
}


fun Routing.configureGamesEndpoint(db: AppDatabase) {
    authenticate(AUTH_ADMIN) {
        // Create game
        post<Games> {
            val game = call.receive<Game>()
            db.games.create(game).onSuccess {
                call.respond(HttpStatusCode.Created, it.value)
            }.onFailure { ex ->
                call.respond(HttpStatusCode.Conflict, ex.message!!)
            }
        }

        // Update game
        patch<Games.Id> {
            val game = call.receive<Game>()
            db.games.update(it.id, game).onSuccess { updated ->
                if (updated)
                    call.respond(HttpStatusCode.OK)
                else
                    call.respond(HttpStatusCode.BadRequest, "Game not found")
            }.onFailure {
                call.respond(HttpStatusCode.Conflict)
            }
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
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }
}
