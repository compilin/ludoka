package dev.compilin.ludoka.endpoints

import dev.compilin.ludoka.*
import dev.compilin.ludoka.model.User
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable

@Resource("auth")
internal class Auth {
    @Resource("password")
    data class Password(val parent: Auth) {
        @Resource("{id}")
        data class Id(val parent: Password, val id: Int)
    }
}

@Serializable
internal data class LoginFormData(val username: String, val password: String)

@Serializable
internal data class PasswordFormData(val password: String)

@Serializable
internal data class AuthStatus(
    /** Optional reference to a user object */
    val user: User?
)

fun Routing.configureAuthEndpoint(db: AppDatabase) {
    authenticate(AUTH_SESSION, optional = true) {
        get<Auth> {
            val user = call.principal<UserPrincipal>()?.user
            call.respond(AuthStatus(user))
        }
    }

    post<Auth> {
        val form = call.receive<LoginFormData>()
        val session = call.sessions.get<UserSession>()
        // Prevent switching users without logging out first. If already logged in as the target user, silently accept
        val (code: HttpStatusCode, user: User?) = if (session != null) {
            if (session.user.name == form.username)
                HttpStatusCode.NotModified to session.user // Already logged in as target user
            else
                HttpStatusCode.BadRequest to session.user // Alreeady logged in as different user
        } else {
            val userPass = db.users.readPassword(form.username)
            if (userPass?.second != null && userPass.second.contentEquals(digestFunction(form.password))) {
                call.sessions.set(UserSession(userPass.first))
                HttpStatusCode.OK to userPass.first // Successful login
            } else
                HttpStatusCode.Unauthorized to null  // Invalid credentials
        }

        call.respond(code, AuthStatus(user))
    }

     delete<Auth> {
        if (call.sessions.get<UserSession>() != null) {
            call.sessions.clear<UserSession>()
        }
        call.respond(HttpStatusCode.OK)
    }

    authenticate(AUTH_SESSION) {
        // Change the user's own password
        post<Auth.Password> {
            val password = call.receive<PasswordFormData>().password
            val userId = call.sessions.get<UserSession>()!!.user.id

            if (!validatePassword(password)) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                val hashPass = digestFunction(password)
                require(db.users.updatePassword(userId, hashPass)) { "Failed to update user's password (id = $userId)" }
                call.respond(HttpStatusCode.OK)
            }
        }
    }

    authenticate(AUTH_ADMIN) {
        // Change given user's password
        post<Auth.Password.Id> {
            val password = call.receive<PasswordFormData>().password

            if (!validatePassword(password)) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                val hashPass = digestFunction(password)
                if (db.users.updatePassword(it.id, hashPass))
                    call.respond(HttpStatusCode.OK)
                else
                    call.respond(HttpStatusCode.NotFound)
            }
        }

        // Delete given user's password (disable login)
        delete<Auth.Password.Id> {
            if (db.users.updatePassword(it.id, null))
                call.respond(HttpStatusCode.OK)
            else
                call.respond(HttpStatusCode.NotFound)
        }
    }
}
