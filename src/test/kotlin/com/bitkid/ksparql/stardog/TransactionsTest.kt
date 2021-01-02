package com.bitkid.ksparql.stardog

import com.bitkid.ksparql.ClientConfig
import com.bitkid.ksparql.KSparqlClient
import com.bitkid.ksparql.iri
import com.bitkid.ksparql.test.TestUtils.fetchAllQuery
import com.bitkid.ksparql.test.TestUtils.testEntity
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.eclipse.rdf4j.model.util.ModelBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

@Disabled
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

    @AfterEach
    fun close() {
        runBlocking {
            client.clear()
        }
        client.close()
    }

    @Test
    fun `can do transaction with the client`() {
        val model = ModelBuilder().subject(testEntity)
            .add(iri("http://propi"), "bla")
            .add(iri("http://propi1"), 5)
            .build()

        runBlocking {
            val transaction = client.begin()
            transaction.add(model)
            client.rollback(transaction)
            expectThat(client.query(fetchAllQuery).toList()).hasSize(0)

            val t = client.begin()
            t.add(model)
            expectThat(client.query(fetchAllQuery).toList()).hasSize(0)
            client.commit(t)
            expectThat(client.query(fetchAllQuery).toList()).hasSize(2)
        }
    }

    @Test
    fun `can do closure transaction`() {
        val model = ModelBuilder().subject(testEntity)
            .add(iri("http://propi"), "bla")
            .add(iri("http://propi1"), 5)
            .build()

        runBlocking {
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
    }
}