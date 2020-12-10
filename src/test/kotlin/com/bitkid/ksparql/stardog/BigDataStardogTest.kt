package com.bitkid.ksparql.stardog

import com.bitkid.ksparql.KSparqlClient
import com.bitkid.ksparql.test.TestUtils.iri
import com.bitkid.ksparql.test.TestUtils.testEntity
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
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
class BigDataStardogTest {
    private val repo = SPARQLRepository(
        "http://localhost:5820/test/query",
        "http://localhost:5820/test/update"
    ).apply {
        setUsernameAndPassword("admin", "admin")
        init()
    }

    private val client = KSparqlClient("http://localhost:5820/test/query", "admin", "admin")
    private val queryString = "SELECT ?a ?b ?c WHERE { ?a ?b ?c }"
    private val numberOfEntries = 10
    private val numberOfInvocations = 10

    @BeforeEach
    fun createTestData() {
        repo.connection.use { c ->
            val builder = ModelBuilder()
            builder.subject(testEntity)
            repeat(numberOfEntries) {
                builder.add(iri("http://bla$it"), "bla")
            }
            c.add(builder.build())
        }
    }

    @AfterEach
    fun close() {
        repo.connection.use {
            it.clear()
        }
        repo.shutDown()
        client.close()
    }


    @Test
    fun `can run query against stardog with rdf4j`() {
        val millis = measureAverage {
            repo.connection.use {
                val query = it.prepareTupleQuery(queryString)
                val res = query.evaluate().toList()
                expectThat(res).hasSize(numberOfEntries)
            }
        }
        println("rdf4j $millis ms")
    }

    @Test
    fun `can run query against stardog with ksparql`() {
        val millis = measureAverage {
            runBlocking {
                val res = client.query(queryString).toList()
                expectThat(res).hasSize(numberOfEntries)
            }
        }
        println("ksparql $millis ms")
    }

    private fun measureAverage(repeat: Int = numberOfInvocations, block: () -> Unit): Long {
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