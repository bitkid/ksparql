package com.bitkid.ksparql.test

import org.eclipse.rdf4j.model.impl.SimpleValueFactory

object TestUtils {
    val testEntity = iri("http://test-entity")
    const val dateMillis = 1607078826127
    fun iri(iriString: String) = SimpleValueFactory.getInstance().createIRI(iriString)
}