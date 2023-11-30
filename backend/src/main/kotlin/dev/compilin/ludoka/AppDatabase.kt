package dev.compilin.ludoka

import dev.compilin.ludoka.model.GameService
import dev.compilin.ludoka.model.UserService
import io.ktor.server.application.*
import io.ktor.util.logging.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.SQLException
import java.util.function.Function


class AppDatabase(environment: ApplicationEnvironment, log: Logger) {

    val database = Database.connect(
        driver = environment.config.property("ludoka.db.driver").getString(),
        url = environment.config.property("ludoka.db.url").getString(),
        user = environment.config.property("ludoka.db.user").getString(),
        password = environment.config.property("ludoka.db.password").getString()
    )

    val users = UserService(database, log)
    val games = GameService(database, log)
}


suspend fun <T> dbQuery(block: suspend Transaction.() -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }

class DatabaseConflictException(indices: List<String>) :
    SQLException("Value already in database for fields: " + indices.joinToString())


interface IUniqueColumnsTable<T> {
    companion object {
        const val PRIMARY_KEY = "primary_key"
    }

    data class Entry<T>(val prop: Function<T, *>, val col: Column<*>, val selector: (T) -> Op<Boolean>)

    val table: Table
    val primaryKeySelector: (T) -> Op<Boolean>
    val indexSelectors: (T) -> Map<String, Op<Boolean>>

    /**
     * Checks whether any object in database would cause a conflict on a unique-constrained column
     * @param item: object with the values to check for conflict
     * @param update: if true, exclude items matching the primary key of the given object, for update queries
     * @return List of columns on which a conflict would occur
     */
    fun checkConflicts(item: T, update: Boolean): List<String> {
        val primaryKeySelector = primaryKeySelector(item)
        val primaryKeyUnselector = NotOp(primaryKeySelector)

        val colsSelect = indexSelectors(item) + if (!update) {
            val name =
                if (table.primaryKey!!.columns.size == 1) table.primaryKey!!.columns.first().name else PRIMARY_KEY
            mapOf(name to primaryKeySelector)
        } else
            emptyMap()
        if (colsSelect.isEmpty())
            return emptyList()

        return colsSelect.map {
            /* For each unique index, generate a query that looks for duplicate value with a different primary key.
            * Add the column name as a virtual attribute aliased "column" so we know where the conflict is */
            table.slice(QueryParameter(it.key, VarCharColumnType()).alias("column"))
                .select(if (update) (primaryKeyUnselector and it.value) else it.value)
        }.reduce<AbstractQuery<*>, Query> { acc, query -> acc.union(query) } // Union all of the subqueries
            .map { it[it.fieldIndex.keys.firstOrNull()!!] as String } // Get the "column" value
    }

    /**
     * Same as [checkConflicts], but instead of returning conflicting columns, returns a [Result]
     * @param item: object with the values to check for conflict
     * @param update: if true, exclude items matching the primary key of the given object, for update queries
     * @param fn: function to run if there is no conflict
     * @return a [Result] containing either the result of the transaction if successfully run, or a [DatabaseConflictException]
     */
    fun <R> checkConflictAndRun(item: T, update: Boolean, fn: () -> R): Result<R> {
        val conflict = checkConflicts(item, update)
        return if (conflict.isEmpty())
            Result.success(fn())
        else
            Result.failure(DatabaseConflictException(conflict))
    }

}


class UniqueColumnsTable<T>(
    override val table: Table,
    entryListFn: EntryListBuilder<T>.() -> Unit
) : IUniqueColumnsTable<T> {
    override val primaryKeySelector: (T) -> Op<Boolean>
    override val indexSelectors: (T) -> Map<String, Op<Boolean>>

    class EntryListBuilder<T> {
        val entryList = mutableListOf<IUniqueColumnsTable.Entry<T>>()

        fun <C> entry(col: Column<C>, prop: (T) -> C) {
            entryList.add(IUniqueColumnsTable.Entry(prop, col) { SqlExpressionBuilder.run { col eq prop(it) } })
        }

        fun <C : Comparable<C>> idEntry(col: Column<EntityID<C>>, prop: (T) -> C) {
            entryList.add(IUniqueColumnsTable.Entry(prop, col) { SqlExpressionBuilder.run { col eq prop(it) } })
        }
    }

    init {
        val builder = EntryListBuilder<T>()
        builder.entryListFn()
        val colMap = builder.entryList.associateBy { it.col }
        primaryKeySelector = { item: T ->
            AndOp(table.primaryKey!!.columns.map { colMap[it]!!.selector(item) })
        }
        indexSelectors = { item: T ->
            table.indices.filter { it.unique }.associate { idx: Index ->
                (if (idx.columns.size > 1) idx.indexName else idx.columns[0].name) to AndOp(idx.columns.map {
                    colMap[it]!!.selector(
                        item
                    )
                })
            }
        }
    }
}
