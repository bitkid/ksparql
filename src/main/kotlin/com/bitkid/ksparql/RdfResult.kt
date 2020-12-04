package com.bitkid.ksparql

import org.eclipse.rdf4j.query.BindingSet

data class RdfResult(val bindingNames: List<String>, val bindingSet: BindingSet)