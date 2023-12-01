package dev.compilin.ludoka.endpoints

import dev.compilin.ludoka.*
import dev.compilin.ludoka.model.User
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.Routing

@Suppress("unused")
@Resource("/users")
private data class Users(val parent: ApiBase) {
    @Resource("{id}")
    class Id(val parent: Users, val id: Int)
}


fun Routing.configureUsersEndpoint(db: AppDatabase) {
    authenticate(AUTH_ADMIN) {
        // Create user
        post<Users> {
            val user = call.receive<User>()
            db.users.create(user, null).onSuccess { id ->
                call.respond(HttpStatusCode.Created, id.value)
            }.onFailure {
                call.respond(HttpStatusCode.Conflict, Message(it.message!!))
            }
        }

        // Update user
        patch<Users.Id> {
            val user = call.receive<User>()
            db.users.update(it.id, user).onSuccess { updated ->
                if (updated)
                    call.respond(HttpStatusCode.OK)
                else
                    call.respond(HttpStatusCode.BadRequest)
            }.onFailure { ex ->
                call.respond(HttpStatusCode.Conflict)
            }

        }

        // Delete user
        delete<Users.Id> {
            db.users.delete(it.id)
            call.respond(HttpStatusCode.OK)
        }
    }
    authenticate(AUTH_SESSION, AUTH_ADMIN) {
        // Get user list
        get<Users> {
            val users = db.users.readAll()
            call.respond(HttpStatusCode.OK, users)
        }
        // Read user
        get<Users.Id> {
            val user = db.users.read(it.id)
            if (user != null) {
                call.respond(HttpStatusCode.OK, user)
            } else {
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }
}
