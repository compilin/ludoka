package dev.compilin.ludoka.model

import io.ktor.util.logging.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction


@Serializable
data class Game(val id: Int = -1, val name: String, val steamid: Int? = null)

class GameService(database: Database, @Suppress("UNUSED_PARAMETER") log: Logger) {
    object Games : IntIdTable() {
        val name = varchar("name", 256)
        val steamid = integer("steamid").nullable().uniqueIndex()

        fun read(row: ResultRow): Game = Game(
            row[id].value,
            row[name],
            row[steamid]
        )

        fun <T> write(game: Game): Games.(UpdateBuilder<T>) -> Unit = {
            it[name] = game.name
            it[steamid] = game.steamid
        }
    }

    init {
        transaction(database) {
            SchemaUtils.create(Games)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    suspend fun create(game: Game): Int = dbQuery {
        Games.insert(Games.write(game))[Games.id].value
    }

    suspend fun read(id: Int): Game? {
        return dbQuery {
            Games.select { Games.id eq id }
                .map(Games::read)
                .singleOrNull()
        }
    }

    suspend fun read(ids: List<Int>): List<Game> {
        return dbQuery {
            Games.select { Games.id inList ids }
                .map(Games::read)
        }
    }

    suspend fun readAll(): List<Game> {
        return dbQuery {
            Games.selectAll().map(Games::read)
        }
    }

    suspend fun update(id: Int, game: Game): Boolean = dbQuery {
        Games.update({ Games.id eq id }, body = Games.write(game)) > 0
    }

    suspend fun delete(id: Int): Boolean = dbQuery {
        Games.deleteWhere { Games.id.eq(id) } > 0
    }
}