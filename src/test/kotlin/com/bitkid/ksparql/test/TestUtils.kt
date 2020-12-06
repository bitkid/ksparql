package com.bitkid.ksparql.test

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.impl.SimpleValueFactory

object TestUtils {
    val testEntity: IRI = iri("http://test-entity")
    const val dateMillis = 1607078826127
    const val testQuery = "SELECT ?a ?b ?c WHERE { ?a ?b ?c }"
    fun iri(iriString: String): IRI = SimpleValueFactory.getInstance().createIRI(iriString)
}