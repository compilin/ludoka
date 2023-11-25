package dev.compilin.ludoka.endpoints

import dev.compilin.ludoka.AUTH_ADMIN
import dev.compilin.ludoka.AUTH_SESSION
import dev.compilin.ludoka.AppDatabase
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
private class Users {
    @Resource("{id}")
    class Id(val parent: Users, val id: Int)
}


fun Routing.configureUsersEndpoint(db: AppDatabase) {
    authenticate(AUTH_ADMIN) {
        // Create user
        post<Users> {
            val user = call.receive<User>()
            if (db.users.existsByName(user.name)) {
                call.respond(HttpStatusCode.Conflict, "User already exists with this name")
            } else {
                val id = db.users.create(user, null)
                call.respond(HttpStatusCode.Created, id)
            }
        }

        // Update user
        patch<Users.Id> {
            val user = call.receive<User>()
            db.users.update(it.id, user)
            call.respond(HttpStatusCode.OK)
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
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}
