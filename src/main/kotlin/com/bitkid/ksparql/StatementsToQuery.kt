package com.bitkid.ksparql

import org.eclipse.rdf4j.model.*
import org.eclipse.rdf4j.model.util.Literals
import org.eclipse.rdf4j.query.parser.sparql.SPARQLUtil

/**
 * again basically copied from rdf4j and made it more kotlin style
 */
fun Iterable<Statement>.createInsertDataCommand(vararg contexts: Resource): String {
    return buildString {
        append("INSERT DATA \n")
        append("{ \n")
        if (contexts.isNotEmpty()) {
            for (context in contexts) {
                val namedGraph = if (context is BNode) {
                    // SPARQL does not allow blank nodes as named graph
                    // identifiers, so we need to skolemize
                    // the blank node id.
                    "urn:nodeid:" + context.stringValue()
                } else context.stringValue()

                append("    GRAPH <$namedGraph> { \n")
                createDataBody(this, true)
                append(" } \n")
            }
        } else {
            createDataBody(this, false)
        }
        append("}")
    }
}

fun createClearString(contexts: Array<out Resource?>): String {
    return if (contexts.isEmpty()) {
        "CLEAR ALL"
    } else {
        buildString {
            for (context in contexts) {
                when (context) {
                    null -> {
                        append("CLEAR ALL DEFAULT; ")
                    }
                    is IRI -> {
                        append("CLEAR ALL GRAPH <" + context.stringValue() + ">; ")
                    }
                    else -> {
                        throw RuntimeException("SPARQL does not support named graphs identified by blank nodes.")
                    }
                }
            }
        }
    }
}

fun Iterable<Statement>.createDataBody(qb: StringBuilder, ignoreContext: Boolean) {
    for (st in this) {
        val context = st.context
        if (!ignoreContext) {
            if (context != null) {
                val namedGraph = if (context is BNode) {
                    // SPARQL does not allow blank nodes as named graph
                    // identifiers, so we need to skolemize
                    // the blank node id.
                    "urn:nodeid:" + context.stringValue()
                } else context.stringValue()
                qb.append("    GRAPH <$namedGraph> { \n")
            }
        }
        if (st.subject is BNode) {
            qb.append("_:" + st.subject.stringValue() + " ")
        } else {
            qb.append("<" + st.subject.stringValue() + "> ")
        }
        qb.append("<" + st.predicate.stringValue() + "> ")
        when {
            st.getObject() is Literal -> {
                val lit = st.getObject() as Literal
                qb.append("\"")
                qb.append(SPARQLUtil.encodeString(lit.label))
                qb.append("\"")
                if (Literals.isLanguageLiteral(lit)) {
                    qb.append("@")
                    qb.append(lit.language.get())
                } else {
                    qb.append("^^<" + lit.datatype.stringValue() + ">")
                }
                qb.append(" ")
            }
            st.getObject() is BNode -> {
                qb.append("_:" + st.getObject().stringValue() + " ")
            }
            else -> {
                qb.append("<" + st.getObject().stringValue() + "> ")
            }
        }
        qb.append(". \n")
        if (!ignoreContext && context != null) {
            qb.append("    }\n")
        }
    }
}
