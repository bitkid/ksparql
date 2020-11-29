package com.bitkid.ksparql

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.io.File
import javax.xml.stream.XMLStreamConstants

fun Application.testServer() {
    val xmlFile = File(ClientTest::class.java.getResource("/yournewstyle.xml").toURI())
    val xmlFileSmall = File(ClientTest::class.java.getResource("/yournewstyle_small.xml").toURI())

    routing {
        get("/small-file") {
            call.respondFile(xmlFileSmall)
        }
        get("/file") {
            call.respondFile(xmlFile)
        }
        get("/error") {
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

    @AfterEach
    fun shutdownServer() = server.stop(0, 0)

    @Test
    fun `can get flow of xml events`() {
        val client = Client()
        runBlocking {
            val events = client.getXml("http://localhost:8080/small-file")
            events.collectIndexed { i, v ->
                if (i == 0) {
                    expectThat(v.event).isEqualTo(XMLStreamConstants.START_DOCUMENT)
                }
                if (i == 1) {
                    expectThat(v.event).isEqualTo(XMLStreamConstants.START_ELEMENT)
                    expectThat(v.data.localName).isEqualTo("root")
                    expectThat(v.data.attributeCount).isEqualTo(1)
                    expectThat(v.data.getAttributeLocalName(0)).isEqualTo("targetNamespace")
                }
                if (i == 2) {
                    expectThat(v.event).isEqualTo(XMLStreamConstants.START_ELEMENT)
                    expectThat(v.data.localName).isEqualTo("date")
                }
                if (i == 3) {
                    expectThat(v.event).isEqualTo(XMLStreamConstants.CHARACTERS)
                    expectThat(v.data.text).isEqualTo("2020-11-28 23:10:01")
                }
                if (i == 4) {
                    expectThat(v.event).isEqualTo(XMLStreamConstants.END_ELEMENT)
                }
                if (i == 5) {
                    expectThat(v.event).isEqualTo(XMLStreamConstants.START_ELEMENT)
                    expectThat(v.data.localName).isEqualTo("categories")
                }
                if (i == 6) {
                    expectThat(v.event).isEqualTo(XMLStreamConstants.START_ELEMENT)
                    expectThat(v.data.localName).isEqualTo("name")
                }
                if (i == 7) {
                    expectThat(v.event).isEqualTo(XMLStreamConstants.CDATA)
                    expectThat(v.data.text).isEqualTo("Damskie")
                }
                if (i == 8) {
                    expectThat(v.event).isEqualTo(XMLStreamConstants.END_ELEMENT)
                }
                if (i == 9) {
                    expectThat(v.event).isEqualTo(XMLStreamConstants.END_ELEMENT)
                }
                if (i == 10) {
                    expectThat(v.event).isEqualTo(XMLStreamConstants.END_ELEMENT)
                }
            }
        }
    }
}