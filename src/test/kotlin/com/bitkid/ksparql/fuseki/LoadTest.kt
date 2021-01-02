package com.bitkid.ksparql.fuseki

import com.bitkid.ksparql.ClientConfig
import com.bitkid.ksparql.KSparqlClient
import com.bitkid.ksparql.iri
import com.bitkid.ksparql.test.TestUtils
import com.bitkid.ksparql.test.TestUtils.testEntity
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.apache.jena.fuseki.embedded.FusekiServer
import org.apache.jena.query.DatasetFactory
import org.eclipse.rdf4j.model.util.ModelBuilder
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.hasSize
import kotlin.system.measureTimeMillis


@Disabled
class LoadTest {
    private val databaseName = "test"
    private val server = FusekiServer.create()
        .setPort(0)
        .add("/$databaseName", DatasetFactory.createTxnMem()).build()
        .apply {
            start()
        }

    private val localFusekiConfig = ClientConfig(
        databaseHost = "http://localhost",
        databasePort = server.port,
        databaseName = databaseName,
    )

    private val repo = SPARQLRepository(
        localFusekiConfig.queryUrl,
        localFusekiConfig.updateUrl
    )

    private val client = KSparqlClient(localFusekiConfig)

    private val numberOfEntries = 1000
    private val numberOfInvocations = 100

    @BeforeEach
    fun createTestData() {
        runBlocking {
            val builder = ModelBuilder()
            builder.subject(testEntity)
            repeat(numberOfEntries) {
                builder.add(iri("http://bla$it"), "bla")
            }
            client.add(builder.build())
        }
    }

    @AfterEach
    fun close() {
        repo.shutDown()
        client.close()
        server.stop()
    }


    @Test
    fun `can run query against stardog with rdf4j`() {
        val millis = runBlocking {
            measureAverage {
                repo.connection.use {
                    val query = it.prepareTupleQuery(TestUtils.fetchAllQuery)
                    val res = query.evaluate().toList()
                    expectThat(res).hasSize(numberOfEntries)
                }
            }
        }
        println("rdf4j $millis ms")
    }

    @Test
    fun `can run query against stardog with ksparql`() {
        val millis = runBlocking {
            measureAverage {
                val res = client.query(TestUtils.fetchAllQuery).toList()
                expectThat(res).hasSize(numberOfEntries)
            }
        }
        println("ksparql $millis ms")
    }

    private suspend fun measureAverage(repeat: Int = numberOfInvocations, block: suspend () -> Unit): Long {
        val times = mutableListOf<Long>()
        repeat(repeat) {
            val millis = measureTimeMillis {
                block()
            }
            times.add(millis)
        }
        return times.average().toLong()
    }
}