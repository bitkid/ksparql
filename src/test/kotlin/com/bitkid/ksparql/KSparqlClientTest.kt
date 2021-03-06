package com.bitkid.ksparql

import com.bitkid.ksparql.test.DataFetcher
import com.bitkid.ksparql.test.TestServer
import com.bitkid.ksparql.test.TestUtils
import io.ktor.http.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.eclipse.rdf4j.query.resultio.text.csv.SPARQLResultsCSVWriter
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue
import java.io.ByteArrayOutputStream


class KSparqlClientTest {
    private val server = TestServer()

    private val repo = SPARQLRepository(
        "http://localhost:${server.port}/test/query"
    ).apply {
        setUsernameAndPassword("admin", "admin")
        init()
    }
    private val bigRepo = SPARQLRepository(
        "http://localhost:${server.port}/test/big-query"
    ).apply {
        setUsernameAndPassword("admin", "admin")
        init()
    }

    private val client = server.testClient()

    @AfterEach
    fun shutdownServer() {
        client.close()
        repo.shutDown()
        bigRepo.shutDown()
        server.close()
    }

    @Test
    fun `can read stardog xml`() {
        runBlocking {
            val result = client.getQueryResult("").toList()
            expectThat(result).hasSize(10)
        }
    }

    @Test
    fun `can read stardog boolean xml`() {
        runBlocking {
            val result = client.getBooleanResult("", "http://localhost:${server.port}/test/boolean")
            expectThat(result).isTrue()
        }
    }

    @Test
    fun `rdf4j and ksparql results are equal`() {
        val res1 = bigRepo.connection.use {
            val query = it.prepareTupleQuery(TestUtils.fetchAllQuery)
            query.evaluate().toList()
        }
        val res2 = runBlocking {
            client.getQueryResult(TestUtils.fetchAllQuery, "http://localhost:${server.port}/test/big-query")
                .map { it.bindingSet }.toList()
        }
        expectThat(res1).isEqualTo(res2)
    }

    @Test
    fun `rdf4j and ksparql csv results are equal`() {
        val csv1 = runBlocking {
            DataFetcher().getString("http://localhost:${server.port}/test/csv")
        }
        val outputStream = ByteArrayOutputStream()
        bigRepo.connection.use {
            val query = it.prepareTupleQuery(TestUtils.fetchAllQuery)
            query.evaluate(SPARQLResultsCSVWriter(outputStream))
        }
        val csv2 = String(outputStream.toByteArray())
        expectThat(csv1).isEqualTo(csv2)
    }

    @Test
    fun `fails if the request fails in an undefined way`() {
        runBlocking {
            val error = expectThrows<HttpRequestException> {
                client.getQueryResult("", "http://localhost:${server.port}/test/error-no-json")
            }
            error.get { message }.isEqualTo("bla")
            error.get { httpStatusCode }.isEqualTo(HttpStatusCode.InternalServerError)
        }
    }
}