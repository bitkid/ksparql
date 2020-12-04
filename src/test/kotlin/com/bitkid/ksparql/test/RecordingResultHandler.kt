package com.bitkid.ksparql.test

import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.query.QueryResultHandler

class RecordingResultHandler : QueryResultHandler {
    var ended = false
    var bindingNames: List<String> = emptyList()
    var bindingSets: List<BindingSet> = emptyList()

    override fun handleBoolean(value: Boolean) {
        TODO("Not yet implemented")
    }

    override fun handleLinks(linkUrls: MutableList<String>?) {
        TODO("Not yet implemented")
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