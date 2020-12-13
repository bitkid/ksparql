package com.bitkid.ksparql

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.impl.SimpleValueFactory

fun iri(iriString: String): IRI = SimpleValueFactory.getInstance().createIRI(iriString)
