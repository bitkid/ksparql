package com.bitkid.ksparql

@Suppress("unused")
object SPARQLResultsXMLConstants {
    const val NAMESPACE = "http://www.w3.org/2005/sparql-results#"
    const val ROOT_TAG = "sparql"
    const val HEAD_TAG = "head"
    const val LINK_TAG = "link"
    const val VAR_TAG = "variable"
    const val VAR_NAME_ATT = "name"
    const val HREF_ATT = "href"
    const val BOOLEAN_TAG = "boolean"
    const val BOOLEAN_TRUE = "true"
    const val BOOLEAN_FALSE = "false"
    const val RESULT_SET_TAG = "results"
    const val RESULT_TAG = "result"
    const val BINDING_TAG = "binding"
    const val BINDING_NAME_ATT = "name"
    const val URI_TAG = "uri"
    const val BNODE_TAG = "bnode"
    const val LITERAL_TAG = "literal"
    const val LITERAL_LANG_ATT = "xml:lang"
    const val LITERAL_DATATYPE_ATT = "datatype"
    const val UNBOUND_TAG = "unbound"
    const val QNAME = "q:qname"

    /* tag constants for serialization of RDF* values in results */
    const val TRIPLE_TAG = "triple"

    /* Stardog variant */
    const val STATEMENT_TAG = "statement"
    const val SUBJECT_TAG = "subject"

    /* Stardog variant */
    const val S_TAG = "s"
    const val PREDICATE_TAG = "predicate"

    /* Stardog variant */
    const val P_TAG = "p"
    const val OBJECT_TAG = "object"

    /* Stardog variant */
    const val O_TAG = "o"
}