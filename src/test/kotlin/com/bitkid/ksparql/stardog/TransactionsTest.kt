package com.bitkid.ksparql.stardog

import com.bitkid.ksparql.ClientConfig
import com.bitkid.ksparql.KSparqlClient
import com.bitkid.ksparql.iri
import com.bitkid.ksparql.model
import com.bitkid.ksparql.test.TestUtils.fetchAllQuery
import com.bitkid.ksparql.test.TestUtils.testEntity
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

@Disabled
@Execution(ExecutionMode.SAME_THREAD)
class TransactionsTest {

    private val client = KSparqlClient(
        ClientConfig(
            databaseHost = "http://localhost",
            databasePort = 5820,
            databaseName = "test",
            user = "admin",
            password = "admin"
        )
    )

    private val model = testEntity.model()
        .add(iri("http://propi"), "bla")
        .add(iri("http://propi1"), 5)
        .build()

    @AfterEach
    fun close() {
        runBlocking {
            client.clear()
        }
        client.close()
    }

    @Test
    fun `can add in transaction`() = runBlocking<Unit> {
        val tr = client.begin()
        tr.add(model)
        tr.rollback()
        expectThat(client.query(fetchAllQuery).toList()).hasSize(0)

        val tc = client.begin()
        tc.add(model)
        expectThat(client.query(fetchAllQuery).toList()).hasSize(0)
        tc.commit()
        expectThat(client.query(fetchAllQuery).toList()).hasSize(2)
    }

    @Test
    fun `can remove in transaction`() = runBlocking<Unit> {
        val ta = client.begin()
        ta.add(model)
        ta.commit()
        expectThat(client.query(fetchAllQuery).toList()).hasSize(2)

        val tr = client.begin()
        tr.remove(testEntity.model().add(iri("http://propi"), "bla").build())
        expectThat(client.query(fetchAllQuery).toList()).hasSize(2)
        tr.commit()
        expectThat(client.query(fetchAllQuery).toList()).hasSize(1)
    }

    @Test
    fun `can add with transaction closure`() = runBlocking<Unit> {
        expectThrows<RuntimeException> {
            client.transaction {
                add(model)
                throw RuntimeException("bla")
            }
        }.get { message }.isEqualTo("bla")
        expectThat(client.query(fetchAllQuery).toList()).hasSize(0)

        client.transaction {
            add(model)
        }
        expectThat(client.query(fetchAllQuery).toList()).hasSize(2)
    }

    @Test
    fun `can remove with transaction closure`() = runBlocking<Unit> {
        client.transaction {
            add(model)
        }
        expectThat(client.query(fetchAllQuery).toList()).hasSize(2)
        client.transaction {
            remove(testEntity.model().add(iri("http://propi"), "bla").build())
        }
        expectThat(client.query(fetchAllQuery).toList()).hasSize(1)
    }
}