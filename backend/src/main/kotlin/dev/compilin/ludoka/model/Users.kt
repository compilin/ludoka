package dev.compilin.ludoka.model

import dev.compilin.ludoka.IUniqueColumnsTable
import dev.compilin.ludoka.UniqueColumnsTable
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
class User(val id: Int = -1, val name: String)

class UserService(database: Database, @Suppress("UNUSED_PARAMETER") log: Logger) {
    object Users : IntIdTable(), IUniqueColumnsTable<User> {
        val name = varchar("name", length = 50).uniqueIndex()
        val password = binary("password").nullable()

        override val table: Table = this
        override val primaryKeySelector: (User) -> Op<Boolean>
        override val indexSelectors: (User) -> Map<String, Op<Boolean>>

        init {
            val uniqueColumns = UniqueColumnsTable(this) {
                idEntry(id, User::id)
                entry(name, User::name)
            }
            primaryKeySelector = uniqueColumns.primaryKeySelector
            indexSelectors = uniqueColumns.indexSelectors
        }

        fun read(row: ResultRow) = User(
            row[id].value,
            row[name])

        fun <T> write(user: User): Users.(UpdateBuilder<T>) -> Unit = {
            it[name] = user.name
        }
    }

    init {
        transaction(database) {
            SchemaUtils.create(Users)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    suspend fun create(user: User, passHash: ByteArray?): Int = dbQuery {
        Users.insertAndGetId {
            write<Number>(user)(it)
            it[password] = passHash
        }.value
    }

    suspend fun read(id: Int): User? {
        return dbQuery {
            Users.select { Users.id eq id }
                .map(Users::read)
                .singleOrNull()
        }
    }

    suspend fun read(ids: List<Int>): List<User> {
        return dbQuery {
            Users.select { Users.id inList ids }
                .map(Users::read)
        }
    }

    suspend fun readAll(): List<User> {
        return dbQuery {
            Users.selectAll().map(Users::read)
        }
    }

    /**
     * Finds a user by name and returns the associated object and password hash
     * @param name of the user to search for
     * @return null if the name is not found, otherwise a pair of the user and password
     */
    suspend fun readPassword(name: String): Pair<User, ByteArray?>? {
        return dbQuery {
            Users.select { Users.name eq name }
                .map { Users.read(it) to it[Users.password] }
                .singleOrNull()
        }
    }

    suspend fun update(id: Int, user: User): Boolean = dbQuery {
        Users.update({ Users.id eq id }, null, Users.write(user)) > 0
    }

    suspend fun updatePassword(id: Int, passHash: ByteArray?) = dbQuery {
        Users.update({ Users.id eq id }) {
            it[password] = passHash
        } > 0
    }

    suspend fun delete(id: Int): Boolean = dbQuery {
        Users.deleteWhere { Users.id.eq(id) } > 0
    }

    suspend fun existsByName(name: String, exceptId: Int? = null): Boolean {
        return dbQuery {
            Users.select { (Users.name eq name) and (Users.id neq exceptId) }.count() > 0
        }
    }
}
