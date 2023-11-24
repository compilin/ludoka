package dev.compilin.ludoka.model

import io.ktor.util.logging.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Describes a user identified by an ID
 */
@Serializable
data class User(val id: Int = -1, val name: String) {
    constructor(it: ResultRow) : this(
        it[UserService.Users.id].value,
        it[UserService.Users.name]
    )

    constructor(id: Int, data: UserData) : this(id, data.name)
}

/**
 * Describe a user's data, without an associated ID, since most API calls receiving user data get the ID separately
 */
@Serializable
data class UserData(val name: String)

class UserService(database: Database, log: Logger) {
    object Users : IntIdTable() {
        val name = varchar("name", length = 50).uniqueIndex()
        val password = binary("password").nullable()
    }

    init {
        transaction(database) {
            SchemaUtils.create(Users)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    suspend fun create(user: UserData, passHash: ByteArray?): Int = dbQuery {
        Users.insert {
            it[name] = user.name
            it[password] = passHash
        }[Users.id].value
    }

    suspend fun read(id: Int): User? {
        return dbQuery {
            Users.select { Users.id eq id }
                .map { User(it) }
                .singleOrNull()
        }
    }

    suspend fun read(ids: List<Int>): List<User> {
        return dbQuery {
            Users.select { Users.id inList ids }
                .map { User(it) }
        }
    }

    suspend fun readAll(): List<User> {
        return dbQuery {
            Users.selectAll().map { User(it) }
        }
    }

    suspend fun readPassword(name: String): Pair<User, ByteArray?>? {
        return dbQuery {
            Users.select { Users.name eq name }
                .map { User(it) to it[Users.password] }
                .singleOrNull()
        }
    }

    suspend fun update(id: Int, user: UserData): Boolean = dbQuery {
        Users.update({ Users.id eq id }) {
            it[name] = user.name
        } > 0
    }

    suspend fun updatePassword(id: Int, passHash: ByteArray?) = dbQuery {
        Users.update({ Users.id eq id }) {
            it[password] = passHash
        } > 0
    }

    suspend fun delete(id: Int): Boolean = dbQuery {
        Users.deleteWhere { Users.id.eq(id) } > 0
    }

    suspend fun existsByName(name: String): Boolean {
        return dbQuery {
            Users.select { Users.name eq name }.count() > 0
        }
    }
}
