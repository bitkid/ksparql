package com.bitkid.ksparql.test

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.query.BindingSet
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

object TestUtils {
    val testEntity: IRI = iri("http://test-entity")
    const val dateMillis = 1607078826127
    const val testQuery = "SELECT ?a ?b ?c WHERE { ?a ?b ?c }"

    fun iri(iriString: String): IRI = SimpleValueFactory.getInstance().createIRI(iriString)

    fun expectResultsForStardogXml(results: List<BindingSet>) {
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

    private fun <T> expectLiteral(
        result: BindingSet,
        predicate: IRI,
        extract: (Literal) -> T,
        value: T
    ) {
        expectThat(result.bindingNames).containsExactly("a", "b", "c")
        expectThat(result.getBinding("a").value).isEqualTo(testEntity)
        expectThat(result.getBinding("b").value).isEqualTo(predicate)
        expectThat(extract(result.getBinding("c").value as Literal)).isEqualTo(value)
    }

    private fun <T> expectValue(
        result: BindingSet,
        predicate: IRI,
        extract: (Value) -> T,
        value: T
    ) {
        expectThat(result.bindingNames).containsExactly("a", "b", "c")
        expectThat(result.getBinding("a").value).isEqualTo(testEntity)
        expectThat(result.getBinding("b").value).isEqualTo(predicate)
        expectThat(extract(result.getBinding("c").value)).isEqualTo(value)
    }
}