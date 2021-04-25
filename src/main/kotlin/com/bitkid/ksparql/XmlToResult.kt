package com.bitkid.ksparql

import com.fasterxml.aalto.AsyncByteArrayFeeder
import com.fasterxml.aalto.AsyncXMLStreamReader
import com.fasterxml.aalto.stax.InputFactoryImpl
import io.ktor.utils.io.*
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.query.impl.MapBindingSet
import javax.xml.stream.XMLStreamConstants

/**
 * convert sparql xml data read from a @ByteReadChannel to a
 * flow of @RdfResult objects. again this heavily uses rdf4j code
 *
 * @see org.eclipse.rdf4j.query.resultio.sparqlxml.SPARQLResultsXMLConstants
 * @see org.eclipse.rdf4j.query.resultio.sparqlxml.SPARQLResultsSAXParser
 */
internal fun ByteReadChannel.getQueryResults(
    valueFactory: ValueFactory = SimpleValueFactory.getInstance(),
    bufferSize: Int = 1024 * 100
) = flow {
    val byteBuffer = ByteArray(bufferSize)
    val reader = InputFactoryImpl().createAsyncForByteArray()
    val context = MutableParseContext()
    do {
        val currentRead = readAvailable(byteBuffer, 0, bufferSize)
        reader.inputFeeder.feedInput(byteBuffer, 0, currentRead)
        emitAvailableResults(reader, context, valueFactory)
    } while (currentRead >= 0)
    reader.inputFeeder.endOfInput()
}

internal suspend fun ByteReadChannel.getBooleanResult(
    bufferSize: Int = 1024 * 100
): Boolean {
    val byteBuffer = ByteArray(bufferSize)
    val reader = InputFactoryImpl().createAsyncForByteArray()
    val stringBuilder = StringBuilder()
    do {
        val currentRead = readAvailable(byteBuffer, 0, bufferSize)
        reader.inputFeeder.feedInput(byteBuffer, 0, currentRead)
        while (reader.hasNext()) {
            val event = reader.next()
            if (event == AsyncXMLStreamReader.EVENT_INCOMPLETE)
                break
            if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)
                if (!reader.isWhiteSpace)
                    stringBuilder.append(reader.text)
            if (event == XMLStreamConstants.END_ELEMENT) {
                if (reader.localName == SPARQLResultsXMLConstants.BOOLEAN_TAG) {
                    reader.inputFeeder.endOfInput()
                    return stringBuilder.toString().toBoolean()
                }
                stringBuilder.clear()
            }
        }
    } while (currentRead >= 0)
    throw RuntimeException("Boolean response not found in XML")
}


/**
 * convert sparql xml data to a flow of @RdfResult objects
 */
internal fun ByteArray.getQueryResults(
    valueFactory: ValueFactory = SimpleValueFactory.getInstance()
) = flow {
    val reader = InputFactoryImpl().createAsyncForByteArray()

    reader.inputFeeder.feedInput(this@getQueryResults, 0, this@getQueryResults.size)
    reader.inputFeeder.endOfInput()

    val context = MutableParseContext()
    emitAvailableResults(reader, context, valueFactory)
}

/**
 * again, this was "inspired" by rdf4j sax parsing, adapted to the
 * aalto async xml library.
 */
private suspend fun FlowCollector<RdfResult>.emitAvailableResults(
    reader: AsyncXMLStreamReader<AsyncByteArrayFeeder>,
    context: MutableParseContext,
    valueFactory: ValueFactory
) {
    while (reader.hasNext()) {
        val event = reader.next()
        if (event == AsyncXMLStreamReader.EVENT_INCOMPLETE)
            break
        if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)
            if (!reader.isWhiteSpace)
                context.currentRawValue.append(reader.text)
        if (event == XMLStreamConstants.START_ELEMENT) {
            context.currentAttributes = (0 until reader.attributeCount).associate {
                reader.getAttributeLocalName(it) to reader.getAttributeValue(it)
            }
            when (reader.localName) {
                SPARQLResultsXMLConstants.VAR_TAG -> {
                    context.headerNames =
                        context.headerNames + context.currentAttributes.getValue(SPARQLResultsXMLConstants.VAR_NAME_ATT)
                }
                SPARQLResultsXMLConstants.RESULT_TAG -> {
                    context.currentBindingSet = MapBindingSet(context.headerNames.size)
                }
                SPARQLResultsXMLConstants.BINDING_TAG -> {
                    context.currentBindingName =
                        context.currentAttributes.getValue(SPARQLResultsXMLConstants.BINDING_NAME_ATT)
                }
            }
        }

        if (event == XMLStreamConstants.END_ELEMENT) {
            when (reader.localName) {
                SPARQLResultsXMLConstants.URI_TAG -> {
                    context.currentValue = valueFactory.createIRI(context.currentRawValue.toString())
                }
                SPARQLResultsXMLConstants.BINDING_TAG -> {
                    context.currentBindingSet.addBinding(
                        context.currentBindingName,
                        context.currentValue
                    )
                }
                SPARQLResultsXMLConstants.RESULT_TAG -> {
                    emit(RdfResult(context.headerNames, context.currentBindingSet))
                }
                SPARQLResultsXMLConstants.LITERAL_TAG -> {
                    val xmlLang = context.currentAttributes[SPARQLResultsXMLConstants.LITERAL_LANG_ATT]
                    val datatype = context.currentAttributes[SPARQLResultsXMLConstants.LITERAL_DATATYPE_ATT]

                    context.currentValue = when {
                        xmlLang != null -> {
                            valueFactory.createLiteral(context.currentRawValue.toString(), xmlLang)
                        }
                        datatype != null -> {
                            valueFactory.createLiteral(
                                context.currentRawValue.toString(),
                                valueFactory.createIRI(datatype)
                            )
                        }
                        else -> {
                            valueFactory.createLiteral(context.currentRawValue.toString())
                        }
                    }
                }
            }
            context.currentRawValue.clear()
        }
    }
}

private class MutableParseContext {
    var headerNames = persistentListOf<String>()
    var currentBindingSet = MapBindingSet(0)
    var currentBindingName = ""
    var currentValue: Value = SimpleValueFactory.getInstance().createLiteral(0)
    var currentRawValue = StringBuilder(1024)
    var currentAttributes = mapOf<String, String>()
}

@Suppress("unused")
private object SPARQLResultsXMLConstants {
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
