package com.bitkid.ksparql

import com.fasterxml.aalto.AsyncByteArrayFeeder
import com.fasterxml.aalto.AsyncXMLStreamReader
import com.fasterxml.aalto.stax.InputFactoryImpl
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.eclipse.rdf4j.model.*
import org.eclipse.rdf4j.model.datatypes.XMLDatatypeUtil
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.query.QueryResultHandler
import org.eclipse.rdf4j.query.impl.MapBindingSet
import javax.xml.stream.XMLStreamConstants

@ExperimentalCoroutinesApi
suspend fun Flow<RdfResult>.handleWith(
    handler: QueryResultHandler
) {
    onCompletion { handler.endQueryResult() }.collectIndexed { index, result ->
        if (index == 0) {
            handler.startQueryResult(result.bindingNames)
        }
        handler.handleSolution(result.bindingSet)
    }
}

private const val lineSeparator = "\r\n"
private val lineSeparatorArray = lineSeparator.toByteArray()
private val valueSeparatorArray = ",".toByteArray()
private val quoteArray = "\"".toByteArray()
private val bNodePrefix = "_:".toByteArray()

suspend fun Flow<RdfResult>.writeCSVTo(channel: ByteWriteChannel) {
    collectIndexed { index, result ->
        if (index == 0) {
            val headers = result.bindingNames.joinToString(separator = ",", postfix = lineSeparator).toByteArray()
            channel.writeFully(headers)
        }
        result.bindingNames.forEachIndexed { i, name ->
            when (val value = result.bindingSet.getValue(name)) {
                is Literal -> writeLiteral(value, channel)
                is Resource -> writeResource(value, channel)
                else -> throw RuntimeException("value should be either Literal or Resource")
            }
            if (i < result.bindingNames.size - 1) {
                channel.writeFully(valueSeparatorArray)
            }
        }
        channel.writeFully(lineSeparatorArray)
    }
}

private suspend fun writeResource(res: Resource, channel: ByteWriteChannel) {
    if (res is IRI) {
        val uriString = res.toString()
        val quoted = uriString.contains(",")
        if (quoted) {
            channel.writeFully(quoteArray)
        }
        channel.writeFully(uriString.toByteArray())
        if (quoted) {
            channel.writeFully(quoteArray)
        }
    } else {
        channel.writeFully(bNodePrefix)
        channel.writeFully((res as BNode).id.toByteArray())
    }
}

private suspend fun writeLiteral(literal: Literal, channel: ByteWriteChannel) {
    val label = literal.label
    val datatype = literal.datatype
    if (XMLDatatypeUtil.isIntegerDatatype(datatype) || XMLDatatypeUtil.isDecimalDatatype(datatype)
        || XSD.DOUBLE == datatype
    ) {
        try {
            val normalized = XMLDatatypeUtil.normalize(label, datatype)
            channel.writeFully(normalized.toByteArray())
            return
        } catch (e: IllegalArgumentException) {
            // not a valid numeric datatyped literal. ignore error and write as
            // (optionally quoted) string instead.
        }
    }
    val quoted = label.contains(",") || label.contains("\r") || label.contains("\n") || label.contains("\"")
    val writeLabel = if (quoted) {
        // escape quotes inside the string
        label.replace("\"".toRegex(), "\"\"")
    } else label

    if (quoted) {
        channel.writeFully(quoteArray)
    }
    channel.writeFully(writeLabel.toByteArray())
    if (quoted) {
        channel.writeFully(quoteArray)
    }
}

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

internal fun ByteArray.getData(
    valueFactory: ValueFactory = SimpleValueFactory.getInstance()
) = flow {
    val reader = InputFactoryImpl().createAsyncForByteArray()

    reader.inputFeeder.feedInput(this@getData, 0, this@getData.size)
    reader.inputFeeder.endOfInput()

    val context = MutableParseContext()
    emitAvailableResults(reader, context, valueFactory)
}

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
