package com.bitkid.ksparql

import kotlinx.coroutines.flow.collect
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
import strikt.assertions.containsExactly
import strikt.assertions.hasSize
import java.util.*

fun iri(iriString: String): IRI {
    return SimpleValueFactory.getInstance().createIRI(iriString)
}

val testEntity = iri("http://test-entity")
const val dateMillis = 1607078826127

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

    private val queryString = "SELECT ?a ?b ?c WHERE { ?a ?b ?c }"

    @BeforeEach
    fun createTestData() {
        BlockHound.install()

        repo.connection.use {
            val builder = ModelBuilder()
            builder.subject(testEntity)
            builder.add(
                iri("http://hasEntityRelation"),
                iri("http://test-ref/${UUID.randomUUID()}")
            )
            builder.add(
                iri("http://hasStringLiteral"),
                "some string"
            )
            builder.add(
                iri("http://hasIntLiteral"),
                234
            )
            builder.add(
                iri("http://hasDateLiteral"),
                Date(dateMillis)
            )
            builder.add(
                iri("http://hasFloatLiteral"),
                1.26.toFloat()
            )
            builder.add(
                iri("http://hasDoubleLiteral"),
                1.23
            )
            builder.add(
                iri("http://hasByteLiteral"),
                4.toByte()
            )
            builder.add(
                iri("http://hasShortLiteral"),
                5.toShort()
            )
            builder.add(
                iri("http://hasLongLiteral"),
                6.toLong()
            )
            builder.add(
                iri("http://hasBooleanLiteral"),
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
            client.executeQuery(queryString) {
                addBinding("b", it.createIRI("http://hasEntityRelation"))
            }.collect {
                expectThat(it.bindingNames).containsExactly("a", "b", "c")
            }
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

    @Disabled
    @Test
    fun `print error xml`() {
        runBlocking {
            println(client.getQueryResponseAsString("slelect * from"))
        }
    }

    @Disabled
    @Test
    fun `print xml`() {
        runBlocking {
            println(client.getQueryResponseAsString(queryString))
        }
    }
}