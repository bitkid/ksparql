package com.bitkid.ksparql

import com.bitkid.ksparql.test.TestUtils.expectResultsForStardogXml
import kotlinx.coroutines.runBlocking
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.query.QueryResultHandler
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isTrue
import java.io.File

class ResultHandlerAdapterTest {

    private val xmlBytes = File(XmlToResultTest::class.java.getResource("/stardog.xml").toURI()).readBytes()

    @Test
    fun `can write to handler`() {
        runBlocking {
            val handler = RecordingResultHandler()
            xmlBytes.getQueryResults().handleWith(handler)
            expectThat(handler.bindingNames).containsExactly("a", "b", "c")
            expectThat(handler.ended).isTrue()
            expectResultsForStardogXml(handler.bindingSets.toList())
        }
    }
}

class RecordingResultHandler : QueryResultHandler {
    var ended = false
    var bindingNames: List<String> = emptyList()
    var bindingSets: List<BindingSet> = emptyList()

    override fun handleBoolean(value: Boolean) {
        throw UnsupportedOperationException()
    }

    override fun handleLinks(linkUrls: MutableList<String>?) {
        throw UnsupportedOperationException()
    }

    override fun startQueryResult(bindingNames: List<String>) {
        this.bindingNames = bindingNames
    }

    override fun endQueryResult() {
        ended = true
    }

    override fun handleSolution(bindingSet: BindingSet) {
        bindingSets = bindingSets + listOf(bindingSet)
    }

}