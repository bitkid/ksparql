package com.bitkid.ksparql

import io.ktor.application.*
import io.ktor.client.features.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.blockhound.BlockHound
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.hasSize
import java.io.File

fun Application.testServer() {
    val xmlStardog = File(ClientTest::class.java.getResource("/stardog.xml").toURI())

    routing {
        get("test/query") {
            call.respondFile(xmlStardog)
        }
        get("test/error") {
            throw RuntimeException()
        }
    }
}

class ClientTest {
    private val server = embeddedServer(
        Netty,
        port = 8080,
        module = Application::testServer
    ).apply {
        start(wait = false)
    }

    private val client = Client("http://localhost:8080/test")

    @BeforeEach
    fun blockHound() {
        BlockHound.install()
    }

    @AfterEach
    fun shutdownServer() {
        client.close()
        server.stop(0, 0)
    }

    @Test
    fun `can read stardog xml`() {
        runBlocking {
            val result = client.getRdfResults("").toList()
            expectThat(result).hasSize(10)
        }
    }

    @Test
    fun `fails if the request fails`() {
        runBlocking {
            expectThrows<ServerResponseException> {
                client.getRdfResults("", "/error")
            }
        }
    }
}