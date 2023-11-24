package dev.compilin.ludoka

import dev.compilin.ludoka.model.User
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.util.*

const val AUTH_SESSION = "auth-user"
const val AUTH_ADMIN = "auth-admin"

data class UserSession(val user: User)
data class UserPrincipal(val user: User) : Principal
object AdminPrincipal : Principal

val digestFunction = getDigestFunction("SHA-256") { "ludoka${it.length}" }

/**
 * Validates password requirements
 * @param password password to validate
 * @return Whether the password passes the requirements
 */
fun validatePassword(password: String): Boolean {
    return password.length > 3
}

fun Application.configureSecurity(db: AppDatabase) {

    val adminToken = environment.config.property("ludoka.admin_token").getString()

    install(Sessions) {
        cookie<UserSession>("LUDOKA_SESSION", SessionStorageMemory()) {
            cookie.path = "/"
            cookie.extensions["SameSite"] = "strict"
        }
    }

    authentication {
        bearer(AUTH_ADMIN) {
            authenticate { tokenCredential ->
                if (tokenCredential.token == adminToken) {
                    AdminPrincipal
                } else {
                    null
                }
            }
        }

        session<UserSession>(AUTH_SESSION) {
            validate { session ->
                UserPrincipal(session.user)
            }
            challenge {
                call.respond(HttpStatusCode.Unauthorized, "You need to be logged in")
            }
        }
    }
}
