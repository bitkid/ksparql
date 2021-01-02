package com.bitkid.ksparql

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.util.ModelBuilder

fun iri(iriString: String): IRI = SimpleValueFactory.getInstance().createIRI(iriString)

fun IRI.model(): ModelBuilder = ModelBuilder().subject(this)