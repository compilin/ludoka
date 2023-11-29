package dev.compilin.ludoka.model

import dev.compilin.ludoka.DatabaseConflictException
import dev.compilin.ludoka.IUniqueColumnsTable
import dev.compilin.ludoka.UniqueColumnsTable
import io.ktor.util.logging.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction


@Serializable
data class Game(val id: Int = -1, val name: String, val steamid: Int? = null)

class GameService(database: Database, @Suppress("UNUSED_PARAMETER") log: Logger) {
    object Games : IntIdTable(), IUniqueColumnsTable<Game> {
        val name = varchar("name", 256)
        val steamid = integer("steamid").nullable().uniqueIndex()

        override val table: Table = this
        override val primaryKeySelector: (Game) -> Op<Boolean>
        override val indexSelectors: (Game) -> Map<String, Op<Boolean>>

        init {
            val uniqueColumns = UniqueColumnsTable(this) {
                idEntry(id, Game::id)
                entry(name, Game::name)
            }
            primaryKeySelector = uniqueColumns.primaryKeySelector
            indexSelectors = uniqueColumns.indexSelectors
        }


        fun read(row: ResultRow): Game = Game(
            row[id].value,
            row[name],
            row[steamid]
        )

        fun <T> write(game: Game): Games.(UpdateBuilder<T>) -> Unit = {
            it[name] = game.name
            it[steamid] = game.steamid
        }

        /**
         * Generates a select query to check for record rows that would cause a unique constraint violation error on
         * insert or update
         * @param game: game with properties to check for duplicates
         * @return a list of conflicting games
         */
        fun SqlExpressionBuilder.findConflict(game: Game): Op<Boolean> =
            (id neq game.id) and (steamid eq game.steamid)


    }

    init {
        transaction(database) {
            SchemaUtils.create(Games)
        }
    }

    suspend fun <T> dbQuery(block: suspend Transaction.() -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    /**
     * Creates a new game in database
     * @param game: game to insert data from into database (id is ignored)
     * @return a [Result<Int>] containing the ID of the game if successfully created, otherwise a [DatabaseConflictException]
     */
    suspend fun create(game: Game): Result<EntityID<Int>> = dbQuery {
        Games.checkConflictAndRun(game, false) {
            Games.insertAndGetId(Games.write(game))
        }
    }

    /**
     * Finds and returns a game by its ID
     * @throws DatabaseConflictException
     */
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

    suspend fun update(id: Int, game: Game): Result<Boolean> = dbQuery {
        Games.checkConflictAndRun(game, true) {
            Games.update({ Games.id eq id }, body = Games.write(game)) > 0
        }
    }

    suspend fun delete(id: Int): Boolean = dbQuery {
        Games.deleteWhere { Games.id.eq(id) } > 0
    }
}