package com.bitkid.ksparql.stardog

import com.bitkid.ksparql.KSparqlClient
import com.bitkid.ksparql.iri
import com.bitkid.ksparql.stardog.LocalStardogTest.Companion.localStardogConfig
import com.bitkid.ksparql.test.TestUtils
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
        localStardogConfig.queryUrl,
        localStardogConfig.updateUrl
    ).apply {
        setUsernameAndPassword(localStardogConfig.user, localStardogConfig.password)
        init()
    }

    private val client = KSparqlClient(localStardogConfig)

    private val numberOfEntries = 10
    private val numberOfInvocations = 10

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
        runBlocking {
            client.clear()
        }
        repo.shutDown()
        client.close()
    }


    @Test
    fun `can run query against stardog with rdf4j`() {
        val millis = measureAverage {
            repo.connection.use {
                val query = it.prepareTupleQuery(TestUtils.fetchAllQuery)
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
                val res = client.query(TestUtils.fetchAllQuery).toList()
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