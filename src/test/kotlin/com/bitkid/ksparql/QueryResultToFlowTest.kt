package com.bitkid.ksparql

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.Value
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import java.io.File

class QueryResultToFlowTest {

    @Test
    fun `can get flow for xml byte array`() {
        val xmlBytes = File(QueryResultToFlowTest::class.java.getResource("/stardog.xml").toURI()).readBytes()
        runBlocking {
            val results = xmlBytes.getData().toList()
            expectThat(results).hasSize(10)

            expectLiteral(results[0], iri("http://hasStringLiteral"), { it.stringValue() }, "some string")
            expectValue(
                results[1],
                iri("http://hasEntityRelation"),
                { it as IRI },
                iri("http://test-ref/4eda5172-8fc9-4295-aecf-d6a21d10a55a")
            )
            expectLiteral(results[2], iri("http://hasByteLiteral"), { it.byteValue() }, 4)
            expectLiteral(results[3], iri("http://hasShortLiteral"), { it.shortValue() }, 5)
            expectLiteral(results[4], iri("http://hasLongLiteral"), { it.longValue() }, 6)
            expectLiteral(results[5], iri("http://hasIntLiteral"), { it.intValue() }, 234)
            expectLiteral(results[6], iri("http://hasFloatLiteral"), { it.floatValue() }, 1.26F)
            expectLiteral(results[7], iri("http://hasDoubleLiteral"), { it.doubleValue() }, 1.23)
            expectLiteral(
                results[8],
                iri("http://hasDateLiteral"),
                { it.calendarValue().toGregorianCalendar().toZonedDateTime().toInstant().toEpochMilli() },
                dateMillis
            )
            expectLiteral(results[9], iri("http://hasBooleanLiteral"), { it.booleanValue() }, true)
        }
    }

    private fun <T> expectLiteral(
        result: RdfResult,
        predicate: IRI,
        extract: (Literal) -> T,
        value: T
    ) {
        expectThat(result.bindingSet.bindingNames).containsExactly("a", "b", "c")
        expectThat(result.bindingNames).containsExactly("a", "b", "c")
        expectThat(result.bindingSet.getBinding("a").value).isEqualTo(testEntity)
        expectThat(result.bindingSet.getBinding("b").value).isEqualTo(predicate)
        expectThat(extract(result.bindingSet.getBinding("c").value as Literal)).isEqualTo(value)
    }

    private fun <T> expectValue(
        result: RdfResult,
        predicate: IRI,
        extract: (Value) -> T,
        value: T
    ) {
        expectThat(result.bindingSet.bindingNames).containsExactly("a", "b", "c")
        expectThat(result.bindingNames).containsExactly("a", "b", "c")
        expectThat(result.bindingSet.getBinding("a").value).isEqualTo(testEntity)
        expectThat(result.bindingSet.getBinding("b").value).isEqualTo(predicate)
        expectThat(extract(result.bindingSet.getBinding("c").value)).isEqualTo(value)
    }
}