package dev.compilin.ludoka

import dev.compilin.ludoka.model.GameService
import dev.compilin.ludoka.model.UserService
import io.ktor.server.application.*
import io.ktor.util.logging.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
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

class DatabaseConflictException(@Suppress("Unused") val columns: List<String>) :
    SQLException("Operation would violate unique constraint on columns: " + columns.joinToString())


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

        val colsSelect = indexSelectors(item) + if (!update)
            mapOf(PRIMARY_KEY to primaryKeySelector)
        else
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
     * Same as [checkConflicts], but instead of returning conflicting columns, throws an exception if there is any
     * @param item: object with the values to check for conflict
     * @param update: if true, exclude items matching the primary key of the given object, for update queries
     * @throws DatabaseConflictException if there is a conflict on any column
     */
    fun checkConflictOrThrow(item: T, update: Boolean) {
        val conflict = checkConflicts(item, update)
        if (conflict.isNotEmpty())
            throw DatabaseConflictException(conflict)
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
                idx.indexName to AndOp(idx.columns.map { colMap[it]!!.selector(item) })
            }
        }
    }
}
