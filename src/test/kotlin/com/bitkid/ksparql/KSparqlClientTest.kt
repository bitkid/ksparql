package com.bitkid.ksparql

import com.bitkid.ksparql.test.FreePorts
import com.bitkid.ksparql.test.TestUtils
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.eclipse.rdf4j.query.resultio.text.csv.SPARQLResultsCSVWriter
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.startsWith
import java.io.ByteArrayOutputStream
import java.io.File

fun Application.testServer() {
    val xmlStardog = File(KSparqlClientTest::class.java.getResource("/stardog.xml").toURI())
    val xmlStardogBig = File(KSparqlClientTest::class.java.getResource("/stardog_big.xml").toURI())
    val jsonError = File(KSparqlClientTest::class.java.getResource("/error.json").toURI()).readText()

    routing {
        post("test/query") {
            call.respondFile(xmlStardog)
        }
        get("test/query") {
            call.respondFile(xmlStardog)
        }
        post("test/big-query") {
            call.respondFile(xmlStardogBig)
        }
        get("test/big-query") {
            call.respondFile(xmlStardogBig)
        }
        post("test/error") {
            call.respond(HttpStatusCode.BadRequest, jsonError)
        }
        post("test/error-no-json") {
            call.respond(HttpStatusCode.InternalServerError, "bla")
        }
        get("test/csv") {
            val xmlBytes = withContext(Dispatchers.IO) {
                xmlStardogBig.readBytes()
            }
            call.respondBytesWriter(ContentType.Text.CSV) {
                xmlBytes.getData().writeCSVTo(this)
            }
        }
    }
}

class KSparqlClientTest {
    private val serverPort = FreePorts.select()
    private val server = embeddedServer(
        Netty,
        port = serverPort,
        module = Application::testServer
    ).apply {
        start(wait = false)
    }
    private val repo = SPARQLRepository(
        "http://localhost:$serverPort/test/query",
        "http://localhost:$serverPort/test/update"
    ).apply {
        setUsernameAndPassword("admin", "admin")
        init()
    }
    private val bigRepo = SPARQLRepository(
        "http://localhost:$serverPort/test/big-query",
        "http://localhost:$serverPort/test/update"
    ).apply {
        setUsernameAndPassword("admin", "admin")
        init()
    }

    private val client = KSparqlClient("http://localhost:$serverPort/test")

    @AfterEach
    fun shutdownServer() {
        client.close()
        repo.shutDown()
        bigRepo.shutDown()
        server.stop(0, 0)
        FreePorts.recycle(serverPort)
    }

    @Test
    fun `can read stardog xml`() {
        runBlocking {
            val result = client.getRdfResults("").toList()
            expectThat(result).hasSize(10)
        }
    }

    @Test
    fun `rdf4j and ksparql results are equal`() {
        val res1 = bigRepo.connection.use {
            val query = it.prepareTupleQuery(TestUtils.testQuery)
            query.evaluate().toList()
        }
        val res2 = runBlocking {
            client.getRdfResults(TestUtils.testQuery, "/big-query").map { it.bindingSet }.toList()
        }
        expectThat(res1).isEqualTo(res2)
    }

    @Test
    fun `rdf4j and ksparql csv results are equal`() {
        val csv1 = runBlocking {
            client.getString("http://localhost:$serverPort/test/csv")
        }
        val outputStream = ByteArrayOutputStream()
        bigRepo.connection.use {
            val query = it.prepareTupleQuery(TestUtils.testQuery)
            query.evaluate(SPARQLResultsCSVWriter(outputStream))
        }
        val csv2 = String(outputStream.toByteArray())
        expectThat(csv1).isEqualTo(csv2)
    }

    @Test
    fun `fails if the request fails in an undefined way`() {
        runBlocking {
            val error = expectThrows<HttpException> {
                client.getRdfResults("", "/error-no-json")
            }
            error.get { message }.isEqualTo("bla")
            error.get { httpStatusCode }.isEqualTo(HttpStatusCode.InternalServerError)
        }
    }

    @Test
    fun `fails if an error is returned`() {
        runBlocking {
            val error = expectThrows<QueryException> {
                client.getRdfResults("", "/error")
            }
            error.get { errorResponse.code }.isEqualTo("QE0PE2")
            error.get { errorResponse.message }.startsWith("com.complexible.stardog")
            error.get { httpStatusCode }.isEqualTo(HttpStatusCode.BadRequest)
        }
    }
}