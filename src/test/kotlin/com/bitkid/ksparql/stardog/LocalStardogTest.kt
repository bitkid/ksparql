package com.bitkid.ksparql.stardog

import com.bitkid.ksparql.KSparqlClient
import com.bitkid.ksparql.QueryException
import com.bitkid.ksparql.iri
import com.bitkid.ksparql.test.TestUtils.dateMillis
import com.bitkid.ksparql.test.TestUtils.fetchAllQuery
import com.bitkid.ksparql.test.TestUtils.testEntity
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.eclipse.rdf4j.model.util.ModelBuilder
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.*
import java.util.*


@Disabled
class LocalStardogTest {
    private val repo = SPARQLRepository(
        "http://localhost:5820/test/query",
        "http://localhost:5820/test/update"
    ).apply {
        setUsernameAndPassword("admin", "admin")
        init()
    }

    private val client = KSparqlClient(
        queryEndpoint = "http://localhost:5820/test/query",
        updateEndpoint = "http://localhost:5820/test/update",
        user = "admin",
        pass = "admin"
    )

    private val queryString = "SELECT ?a ?b ?c WHERE { ?a ?b ?c }"

    @BeforeEach
    fun createTestData() {
        val builder = ModelBuilder()
        builder.subject(testEntity)
        builder.add(
            iri("http://hasEntityRelation"),
            iri("http://test-ref/${UUID.randomUUID()}")
        )
        builder.add(
            iri("http://hasStringLiteral"),
            "  some string "
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
        runBlocking {
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
    fun `results are equal`() {
        val res1 = repo.connection.use {
            val query = it.prepareTupleQuery(queryString)
            query.evaluate().toList()
        }
        val res2 = runBlocking {
            client.query(queryString).map { it.bindingSet }.toList()
        }
        expectThat(res1).isEqualTo(res2)
    }

    @Test
    fun `can add`() {
        val model = ModelBuilder().subject(testEntity)
            .add(iri("http://propi"), "bla")
            .add(iri("http://propi1"), 5)
            .build()

        runBlocking {
            client.add(model, "http://localhost:5820/test/update")
            expectThat(client.query(fetchAllQuery).toList()).hasSize(12)
        }
    }

    @Test
    fun `can run query against stardog with ksparql`() {
        runBlocking {
            val result = client.query(queryString) {
                addBinding("b", it.createIRI("http://hasEntityRelation"))
            }.toList()
            expectThat(result).hasSize(1)
            expectThat(result.single().bindingNames).containsExactly("a", "b", "c")
        }
    }

    @Test
    fun `can run ask query against stardog with ksparql`() {
        runBlocking {
            val result = client.ask("""ASK {?a ?b ?c}""") {
                addBinding("b", it.createIRI("http://hasEntityRelation"))
            }
            expectThat(result).isTrue()
            val result1 = client.ask("""ASK {?a ?b ?c}""") {
                addBinding("c", it.createLiteral("not existing"))
            }
            expectThat(result1).isFalse()
        }
    }

    @Test
    fun `can handle query error`() {
        runBlocking {
            expectThrows<QueryException> {
                client.getQueryResult("slelect bla *")
            }
        }
    }

    @Test
    fun `print error json`() {
        runBlocking {
            println(
                DataFetcher().getQueryResponseAsString(
                    "http://localhost:5820/test/query",
                    "slelect * from"
                )
            )
        }
    }

    @Test
    fun `print xml`() {
        runBlocking {
            println(DataFetcher().getQueryResponseAsString("http://localhost:5820/test/query", queryString))
        }
    }
}