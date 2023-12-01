package dev.compilin.ludoka.model

import dev.compilin.ludoka.dbQuery
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeCollection
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class LibraryEntry(val user_id: Int = -1, val game_id: Int = -1, val interest: Boolean) {
    companion object {
        val DEFAULTS = LibraryEntry(interest = false)
        val COLUMNS = listOf("user_id", "game_id", "interest")
    }

    class AsListSerializer : KSerializer<LibraryEntry> {
        override val descriptor: SerialDescriptor = serialDescriptor<List<Int>>()

        override fun serialize(encoder: Encoder, value: LibraryEntry) =
            encoder.encodeCollection(descriptor, COLUMNS.size) {
                encodeIntElement(serialDescriptor<Int>(), 0, value.user_id)
                encodeIntElement(serialDescriptor<Int>(), 1, value.game_id)
                encodeBooleanElement(serialDescriptor<Boolean>(), 2, value.interest)
            }

        override fun deserialize(decoder: Decoder): LibraryEntry = decoder.decodeStructure(descriptor) {
            val uid = decodeIntElement(serialDescriptor<Int>(), 0)
            val gid = decodeIntElement(serialDescriptor<Int>(), 1)
            val interest = decodeBooleanElement(serialDescriptor<Boolean>(), 2)
            LibraryEntry(uid, gid, interest)
        }
    }

    class AsListOfListsSerializer : KSerializer<List<LibraryEntry>> by ListSerializer(AsListSerializer())

    fun hasDefaultValues(): Boolean {
        return interest == DEFAULTS.interest
    }
}

class LibrariesService(val db: Database) {
    object Libraries : Table("libraries") {
        val user_id = reference("user_id", UserService.Users, onDelete = ReferenceOption.CASCADE)
        val game_id = reference("game_id", GameService.Games, onDelete = ReferenceOption.CASCADE)

        val interest = bool("interest")

        override val primaryKey = PrimaryKey(user_id, game_id)


        fun read(row: ResultRow) = LibraryEntry(
            row[user_id].value,
            row[game_id].value,
            row[interest]
        )

        fun <T> write(entry: LibraryEntry): (UpdateBuilder<T>) -> Unit = {
            it[interest] = entry.interest
        }
    }

    init {
        transaction(db) {
            SchemaUtils.create(Libraries)
        }
    }

    suspend fun get(uid: Int, gid: Int): LibraryEntry = dbQuery {
        Libraries.select {
            (Libraries.user_id eq uid) and (Libraries.game_id eq gid)
        }.map(Libraries::read)
            .singleOrNull() ?: LibraryEntry.DEFAULTS
    }

    suspend fun get(uids: List<Int>, gids: List<Int>?): List<LibraryEntry> = dbQuery {
        Libraries.select {
            val op = (Libraries.user_id inList uids)
            if (gids != null)
                op and (Libraries.game_id inList gids)
            else
                op
        }.map(Libraries::read)
    }

    suspend fun set(uid: Int, gid: Int, entry: LibraryEntry) = dbQuery {
        if (entry.hasDefaultValues())
            unset(uid, gid)
        else
            Libraries.upsert {
                it[user_id] = uid
                it[game_id] = gid
                write<Int>(entry)(it)
            }
    }

    suspend fun unset(uid: Int, gid: Int) = dbQuery {
        Libraries.deleteWhere {
            (user_id eq uid) and (game_id eq gid)
        }
    }
}
