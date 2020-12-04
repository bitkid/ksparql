package com.bitkid.ksparql

import com.bitkid.ksparql.test.FreePorts
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.blockhound.BlockHound
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.startsWith
import java.io.File

fun Application.testServer() {
    val xmlStardog = File(KSparqlClientTest::class.java.getResource("/stardog.xml").toURI())
    val jsonError = File(KSparqlClientTest::class.java.getResource("/error.json").toURI()).readText()

    routing {
        post("test/query") {
            call.respondFile(xmlStardog)
        }
        post("test/error") {
            call.respond(HttpStatusCode.BadRequest, jsonError)
        }
        post("test/error-no-json") {
            call.respond(HttpStatusCode.InternalServerError, "bla")
        }
        post("test/csv") {
            val xmlBytes = withContext(Dispatchers.IO) {
                xmlStardog.readBytes()
            }
            val data = xmlBytes.getData()
            call.respondBytesWriter(ContentType.Text.CSV) {
                data.writeCSVTo(this)
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

    private val client = KSparqlClient("http://localhost:$serverPort/test")

    @BeforeEach
    fun blockHound() {
        BlockHound.install()
    }

    @AfterEach
    fun shutdownServer() {
        client.close()
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
    fun `can convert to CSV`() {
        runBlocking {
            client.getString("http://localhost:$serverPort/test/csv")
        }
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