package com.bitkid.ksparql

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.util.ModelBuilder
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import reactor.blockhound.BlockHound
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.hasSize
import java.util.*

fun iri(iriString: String): IRI {
    return SimpleValueFactory.getInstance().createIRI(iriString)
}

@Disabled
class LocalStardogTest {
    private val repo = SPARQLRepository(
        "http://localhost:5820/test/query",
        "http://localhost:5820/test/update"
    ).apply {
        setUsernameAndPassword("admin", "admin")
        init()
    }

    private val client = KSparqlClient("http://localhost:5820/test")

    private val testEntity = iri("http://test-entity")
    private val queryString = "SELECT * WHERE { ?a ?b ?c }"


    @BeforeEach
    fun createTestData() {
        BlockHound.install()

        repo.connection.use {
            val builder = ModelBuilder()
            builder.subject(testEntity)
            repeat(3) {
                builder.add(
                    iri("http://test-rel/${UUID.randomUUID()}"),
                    iri("http://test-ref/${UUID.randomUUID()}")
                )
            }
            repeat(3) {
                builder.add(
                    iri("http://test-rel/${UUID.randomUUID()}"),
                    "some string"
                )
            }
            repeat(3) {
                builder.add(
                    iri("http://test-rel/${UUID.randomUUID()}"),
                    234
                )
            }
            builder.add(
                iri("http://test-rel/${UUID.randomUUID()}"),
                true
            )
            it.add(builder.build())
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
        repo.connection.use {
            val query = it.prepareTupleQuery(queryString)
            query.setBinding("a", testEntity)
            val result = query.evaluate()
            expectThat(result.toList()).hasSize(10)
        }
    }

    @Test
    fun `can run query against stardog with ksparql`() {
        runBlocking {
            val result = client.getRdfResults(queryString).toList()
            expectThat(result).hasSize(10)
        }
    }

    @Test
    fun `can handle query error`() {
        runBlocking {
            expectThrows<QueryException> {
                client.getRdfResults("slelect bla *")
            }
        }
    }

    @Test
    fun `print error xml`() {
        runBlocking {
            println(client.getString("slelect * from"))
        }
    }

    @Test
    fun `print xml`() {
        runBlocking {
            println(client.getString(queryString))
        }
    }
}