package com.bitkid.ksparql

import com.fasterxml.aalto.AsyncByteArrayFeeder
import com.fasterxml.aalto.AsyncXMLStreamReader
import com.fasterxml.aalto.stax.InputFactoryImpl
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.*
import org.eclipse.rdf4j.model.*
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.query.impl.MapBindingSet
import javax.xml.stream.XMLStreamConstants

/**
 * convert sparql xml data read from a @ByteReadChannel to a
 * flow of @RdfResult objects
 */
internal fun ByteReadChannel.getData(
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

/**
 * convert sparql xml data to a flow of @RdfResult objects
 */
internal fun ByteArray.getData(
    valueFactory: ValueFactory = SimpleValueFactory.getInstance()
) = flow {
    val reader = InputFactoryImpl().createAsyncForByteArray()

    reader.inputFeeder.feedInput(this@getData, 0, this@getData.size)
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
    var headerNames = listOf<String>()
    var currentBindingSet = MapBindingSet(0)
    var currentBindingName = ""
    var currentValue: Value = SimpleValueFactory.getInstance().createLiteral(0)
    var currentRawValue = StringBuilder(1024)
    var currentAttributes = mapOf<String, String>()
}
