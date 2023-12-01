package dev.compilin.ludoka.endpoints

import dev.compilin.ludoka.*
import dev.compilin.ludoka.model.LibraryEntry
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.Routing
import kotlinx.serialization.Serializable

@Resource("/library")
private data class Library(val parent: ApiBase) {
    @Resource("/{uid}/{gid}")
    data class Entry(val parent: Library, val uid: Int, val gid: Int)

    @Resource("")
    data class Group(val parent: Library, val users: String, val games: String? = null)
}


@Serializable
data class GroupEntries(
    @Serializable(with = LibraryEntry.AsListSerializer::class) val defaults: LibraryEntry,
    val columns: List<String>,
    @Serializable(with = LibraryEntry.AsListOfListsSerializer::class) val entries: List<LibraryEntry>
)

fun Routing.configureLibrariesEndpoint(db: AppDatabase) {
    authenticate(AUTH_ADMIN, AUTH_SESSION) {
        get<Library.Entry> {
            val entry = db.libraries.get(it.uid, it.gid)
            call.respond(HttpStatusCode.OK, entry)
        }

        post<Library.Entry> {
            if (call.principal<UserPrincipal>()?.user?.id?.equals(it.uid) == false) {
                call.respond(HttpStatusCode.Unauthorized, Message("You cannot edit another user's library"))
            } else {
                val entry = call.receive<LibraryEntry>()
                db.libraries.set(it.uid, it.gid, entry)
                call.respond(HttpStatusCode.OK)
            }
        }

        delete<Library.Entry> {
            if (call.principal<UserPrincipal>()?.user?.id?.equals(it.uid) == false) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                db.libraries.unset(it.uid, it.gid)
                call.respond(HttpStatusCode.OK, LibraryEntry.DEFAULTS)
            }
        }


        get<Library.Group> { grp ->
            try {
                val uids = grp.users.split(",").map { it.toInt() }
                val gids = grp.games?.split(",")?.map { it.toInt() }
                call.respond(
                    HttpStatusCode.OK, GroupEntries(
                        LibraryEntry.DEFAULTS,
                        LibraryEntry.COLUMNS,
                        db.libraries.get(uids, gids)
                    )
                )
            } catch (ex: NumberFormatException) {
                call.respond(HttpStatusCode.BadRequest, "Invalid IDs list")
            }
        }
    }
}