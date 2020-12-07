package com.bitkid.ksparql

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.onCompletion
import org.eclipse.rdf4j.query.QueryResultHandler

/**
 * this can be used to to connect the result flow to a QueryResultHandler like the
 * SPARQLResultsJSONWriter. Be aware that the rdf4j classes all use a blocking
 * output stream.
 *
 * @see QueryResultHandler
 * @see org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONWriter
 */
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