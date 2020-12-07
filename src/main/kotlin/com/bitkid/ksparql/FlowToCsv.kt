package com.bitkid.ksparql

import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectIndexed
import org.eclipse.rdf4j.model.BNode
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.model.datatypes.XMLDatatypeUtil
import org.eclipse.rdf4j.model.vocabulary.XSD

private const val lineSeparator = "\r\n"
private val lineSeparatorArray = lineSeparator.toByteArray()
private val valueSeparatorArray = ",".toByteArray()
private val quoteArray = "\"".toByteArray()
private val bNodePrefix = "_:".toByteArray()

/**
 * This is mainly copied and optimized from rdf4j, but instead of writing
 * to an output stream this writes to a ByteWriteChannel.
 * The CSV output should be the same though.
 *
 * @see org.eclipse.rdf4j.query.resultio.text.csv.SPARQLResultsCSVWriter
 * @see ByteWriteChannel
 */
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