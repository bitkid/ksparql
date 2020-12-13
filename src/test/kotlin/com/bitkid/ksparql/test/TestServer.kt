package com.bitkid.ksparql.test

import com.bitkid.ksparql.KSparqlClient
import com.bitkid.ksparql.getQueryResults
import com.bitkid.ksparql.writeCSVTo
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TestServer : AutoCloseable {
    val port = FreePorts.select()

    private val server = embeddedServer(
        Netty,
        port = port,
        module = Application::testServer
    ).apply {
        start(wait = false)
    }

    fun testClient(): KSparqlClient {
        return KSparqlClient(
            "http://localhost:${port}/test/query",
            "http://localhost:${port}/test/update",
            "admin",
            "admin"
        )
    }

    override fun close() {
        server.stop(0, 0)
        FreePorts.recycle(port)
    }
}

fun Application.testServer() {
    val xmlStardog = File(TestServer::class.java.getResource("/stardog.xml").toURI())
    val xmlStardogBig = File(TestServer::class.java.getResource("/stardog_big.xml").toURI())
    val xmlStardogBoolean = File(TestServer::class.java.getResource("/stardog_boolean.xml").toURI())
    val jsonError = File(TestServer::class.java.getResource("/error.json").toURI()).readText()

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
        get("test/boolean") {
            call.respondFile(xmlStardogBoolean)
        }
        post("test/boolean") {
            call.respondFile(xmlStardogBoolean)
        }
        post("test/error-no-json") {
            call.respond(HttpStatusCode.InternalServerError, "bla")
        }
        get("test/csv") {
            val xmlBytes = withContext(Dispatchers.IO) {
                xmlStardogBig.readBytes()
            }
            call.respondBytesWriter(ContentType.Text.CSV) {
                xmlBytes.getQueryResults().writeCSVTo(this)
            }
        }
    }
}