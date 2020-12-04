package com.bitkid.ksparql

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Literal
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

            val first = results.first()
            expectThat(first.bindingNames).containsExactly("a", "b", "c")
            expectThat(first.bindingSet.getBinding("a").value).isEqualTo(iri("http://test-entity"))
            expectThat(first.bindingSet.getBinding("b").value).isEqualTo(iri("http://test-rel/c74deaf9-aea1-46b7-aa8c-92f3ac69fd23"))
            expectThat((first.bindingSet.getBinding("c").value as Literal).stringValue()).isEqualTo("some string")

            val fourth = results[3]
            expectThat(fourth.bindingNames).containsExactly("a", "b", "c")
            expectThat(fourth.bindingSet.getBinding("a").value).isEqualTo(iri("http://test-entity"))
            expectThat(fourth.bindingSet.getBinding("b").value).isEqualTo(iri("http://test-rel/b88c5e50-6e00-4a33-9429-8e958b82b102"))
            expectThat(fourth.bindingSet.getBinding("c").value as IRI).isEqualTo(iri("http://test-ref/ff3d15fb-3fe5-4b09-81e1-85b0083471f1"))

            val seventh = results[6]
            expectThat(seventh.bindingNames).containsExactly("a", "b", "c")
            expectThat(seventh.bindingSet.getBinding("a").value).isEqualTo(iri("http://test-entity"))
            expectThat(seventh.bindingSet.getBinding("b").value).isEqualTo(iri("http://test-rel/aad1497b-440f-4aa1-8973-5476f7eb76d7"))
            expectThat((seventh.bindingSet.getBinding("c").value as Literal).intValue()).isEqualTo(234)
        }
    }
}