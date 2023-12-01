package dev.compilin.ludoka

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.config.ConfigLoader.Companion.load
import io.ktor.server.engine.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.AfterClass
import org.junit.BeforeClass
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.expect

@Serializable
class TestData(
    val userid: Int,
    val gameid: Int,
    val interest: Boolean,
    val unique1: String,
    val unique2a: String,
    val unique2b: String
) {
    override fun toString(): String {
        return "TestData(userid: $userid, gameid: $gameid, interest: $interest, unique1: $unique1, unique2a: $unique2a, unique2b: $unique2b)"
    }
}

object TestTable : Table("test"), IUniqueColumnsTable<TestData> {

    val userid = integer("userid")
    val gameid = integer("gameid")

    val interest = bool("interest")
    val unique1 = varchar("unique1", 50).uniqueIndex("unique1_index")
    val unique2a = varchar("unique2a", 50)
    val unique2b = varchar("unique2b", 50)

    override val primaryKey = PrimaryKey(userid, gameid)

    init {
        uniqueIndex("unique2_index", unique2a, unique2b)
    }

    override val table: Table = this
    override val primaryKeySelector: (TestData) -> Op<Boolean>
    override val indexSelectors: (TestData) -> Map<String, Op<Boolean>>

    init {
        val uniqueColumns = UniqueColumnsTable(this) {
            entry(userid, TestData::userid)
            entry(gameid, TestData::gameid)
            entry(interest, TestData::interest)
            entry(unique1, TestData::unique1)
            entry(unique2a, TestData::unique2a)
            entry(unique2b, TestData::unique2b)
        }
        primaryKeySelector = uniqueColumns.primaryKeySelector
        indexSelectors = uniqueColumns.indexSelectors
    }
}

class DatabaseTest {
    companion object {
        lateinit var appdb: AppDatabase
        val log = LoggerFactory.getLogger(DatabaseTest::class.java)!!

        val insertData: UpdateBuilder<*>.(TestData) -> Unit = {
            this[TestTable.userid] = it.userid
            this[TestTable.gameid] = it.gameid
            this[TestTable.interest] = it.interest
            this[TestTable.unique1] = it.unique1
            this[TestTable.unique2a] = it.unique2a
            this[TestTable.unique2b] = it.unique2b
        }

        @JvmStatic
        @BeforeClass
        fun setup() {
            val app = Application(ApplicationEngineEnvironmentBuilder().build {
                config = ConfigLoader.load()
                developmentMode = true
            })
            appdb = AppDatabase(app.environment)

            transaction(appdb.database) {
                log.info("Creating table ${TestTable.tableName}…")
                SchemaUtils.create(TestTable)
                log.info("Inserting data into ${TestTable.tableName}…")
                TestTable.batchInsert(
                    listOf(
                        TestData(1, 1, true, "a", "a", "a"),
                        TestData(1, 2, false, "b", "a", "b"),
                        TestData(1, 3, true, "c", "b", "a"),
                        TestData(2, 1, false, "d", "b", "b"),
                        TestData(2, 2, true, "e", "c", "a"),
                    ), body = insertData
                )

            }
        }

        @JvmStatic
        @AfterClass
        fun teardown() = transaction(appdb.database) {
            log.info("Dropping table ${TestTable.tableName}…")
            SchemaUtils.drop(TestTable)
        }

    }

    @Test
    fun testConflicts() {
        data class TestCase(val data: TestData, val update: Boolean, val expectConflicts: List<String>)

        val PK = IUniqueColumnsTable.PRIMARY_KEY
        val U1 = "unique1_index"
        val U2 = "unique2_index"

        for (case in listOf(
            TestCase(TestData(1, 1, true, "x", "y", "z"), false, listOf(PK)),
            TestCase(TestData(1, 2, true, "b", "y", "z"), true, emptyList()),
            TestCase(TestData(1, 1, true, "b", "y", "z"), true, listOf(U1)),
            TestCase(TestData(1, 1, true, "b", "y", "z"), false, listOf(PK, U1)),
            TestCase(TestData(1, 1, true, "x", "b", "b"), true, listOf(U2)),
            TestCase(TestData(1, 1, true, "x", "a", "a"), false, listOf(PK, U2)),
            TestCase(TestData(2, 1, true, "e", "c", "a"), true, listOf(U1, U2)),
        )) {
            transaction(appdb.database) {
                assertContentEquals(
                    TestTable.checkConflicts(case.data, case.update).sorted(),
                    case.expectConflicts.sorted()
                )

                val expected = if (case.expectConflicts.isEmpty()) 1 else 0
                expect(expected) {
                    try {
                        if (case.update) {
                            TestTable.update({ TestTable.primaryKeySelector(case.data) }) {
                                it.insertData(case.data)
                            }
                        } else {
                            TestTable.insert { it.insertData(case.data) }.insertedCount
                        }
                    } catch (ex: ExposedSQLException) {
                        0
                    }
                }
                rollback()
            }
        }
    }
}