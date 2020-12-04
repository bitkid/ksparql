package com.bitkid.ksparql

import com.fasterxml.aalto.AsyncByteArrayFeeder
import com.fasterxml.aalto.AsyncXMLStreamReader
import com.fasterxml.aalto.stax.InputFactoryImpl
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.query.impl.MapBindingSet
import javax.xml.stream.XMLStreamConstants

class QueryResultToFlow {

    fun getData(
        channel: ByteReadChannel,
        valueFactory: ValueFactory = SimpleValueFactory.getInstance(),
        bufferSize: Int = 1024 * 100
    ) = flow {
        val byteBuffer = ByteArray(bufferSize)
        val parser = InputFactoryImpl().createAsyncForByteArray()
        val mutableParseValues = MutableParseValues(valueFactory.createLiteral(0))

        do {
            val currentRead = channel.readAvailable(byteBuffer, 0, bufferSize)
            if (currentRead > 0) {
                parser.inputFeeder.feedInput(byteBuffer, 0, currentRead)
                emitForAvailableXml(parser, mutableParseValues, valueFactory)
            }
        } while (currentRead >= 0)
        parser.inputFeeder.endOfInput()
    }

    internal fun getData(
        xmlBytes: ByteArray,
        valueFactory: ValueFactory = SimpleValueFactory.getInstance()
    ) = flow {
        val parser = InputFactoryImpl().createAsyncForByteArray()

        parser.inputFeeder.feedInput(xmlBytes, 0, xmlBytes.size)
        parser.inputFeeder.endOfInput()

        val parseValues = MutableParseValues(valueFactory.createLiteral(0))

        emitForAvailableXml(parser, parseValues, valueFactory)
    }
}

private suspend fun FlowCollector<RdfResult>.emitForAvailableXml(
    reader: AsyncXMLStreamReader<AsyncByteArrayFeeder>,
    state: MutableParseValues,
    valueFactory: ValueFactory
) {
    while (reader.hasNext()) {
        val event = reader.next()
        if (event == AsyncXMLStreamReader.EVENT_INCOMPLETE)
            break
        if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)
            state.currentRawValue = reader.text
        if (event == XMLStreamConstants.START_ELEMENT) {
            state.currentAttributes = (0 until reader.attributeCount).associate {
                reader.getAttributeLocalName(it) to reader.getAttributeValue(it)
            }
            when (reader.localName) {
                SPARQLResultsXMLConstants.VAR_TAG -> {
                    state.headerNames =
                        state.headerNames + state.currentAttributes.getValue(SPARQLResultsXMLConstants.VAR_NAME_ATT)
                }
                SPARQLResultsXMLConstants.RESULT_TAG -> {
                    state.currentBindingSet = MapBindingSet(state.headerNames.size)
                }
                SPARQLResultsXMLConstants.BINDING_TAG -> {
                    state.currentBindingName =
                        state.currentAttributes.getValue(SPARQLResultsXMLConstants.BINDING_NAME_ATT)
                }
            }
        }

        if (event == XMLStreamConstants.END_ELEMENT) {
            when (reader.localName) {
                SPARQLResultsXMLConstants.URI_TAG -> {
                    state.currentValue = valueFactory.createIRI(state.currentRawValue)
                }
                SPARQLResultsXMLConstants.BINDING_TAG -> {
                    state.currentBindingSet.addBinding(
                        state.currentBindingName,
                        state.currentValue
                    )
                }
                SPARQLResultsXMLConstants.RESULT_TAG -> {
                    emit(RdfResult(state.headerNames, state.currentBindingSet))
                }
                SPARQLResultsXMLConstants.LITERAL_TAG -> {
                    val xmlLang = state.currentAttributes[SPARQLResultsXMLConstants.LITERAL_LANG_ATT]
                    val datatype = state.currentAttributes[SPARQLResultsXMLConstants.LITERAL_DATATYPE_ATT]

                    state.currentValue = when {
                        xmlLang != null -> {
                            valueFactory.createLiteral(state.currentRawValue, xmlLang)
                        }
                        datatype != null -> {
                            valueFactory.createLiteral(
                                state.currentRawValue,
                                valueFactory.createIRI(datatype)
                            )
                        }
                        else -> {
                            valueFactory.createLiteral(state.currentRawValue)
                        }
                    }
                }
            }
        }
    }
}


private class MutableParseValues(defaultValue: Value) {
    var headerNames = listOf<String>()
    var currentBindingSet = MapBindingSet(0)
    var currentBindingName = ""
    var currentValue: Value = defaultValue
    var currentRawValue = ""
    var currentAttributes = mapOf<String, String>()
}

