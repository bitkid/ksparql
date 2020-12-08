package com.bitkid.ksparql

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.features.auth.*
import io.ktor.client.features.auth.providers.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.query.impl.MapBindingSet
import org.eclipse.rdf4j.repository.sparql.query.QueryStringUtil

class KSparqlClient(
    private val queryEndpoint: String,
    engine: HttpClientEngineFactory<*>,
    userName: String = "admin",
    pw: String = "admin",
    private val readXmlBufferSize: Int = 1024 * 100
) : AutoCloseable {

    private val jackson = jacksonObjectMapper()
    private val valueFactory = SimpleValueFactory.getInstance()
    private val client = HttpClient(engine) {
        expectSuccess = false
        install(Auth) {
            basic {
                username = userName
                password = pw
            }
        }
    }

    suspend fun query(
        query: String,
        bindings: MapBindingSet.(vf: ValueFactory) -> Unit = {}
    ): Flow<RdfResult> {
        val bindingSet = createBindingSet(bindings)
        val queryString = QueryStringUtil.getTupleQueryString(query, bindingSet)
        return getQueryResult(queryString)
    }

    suspend fun ask(
        query: String,
        bindings: MapBindingSet.(vf: ValueFactory) -> Unit = {}
    ): Boolean {
        val bindingSet = createBindingSet(bindings)
        val queryString = QueryStringUtil.getBooleanQueryString(query, bindingSet)
        return getBooleanResult(queryString)
    }

    internal suspend fun getBooleanResult(
        query: String,
        endpoint: String = queryEndpoint
    ) = getSparqlResult(query, endpoint) {
        it.receive<ByteReadChannel>().getBooleanResult(
            bufferSize = readXmlBufferSize
        )
    }

    internal suspend fun getQueryResult(
        query: String,
        endpoint: String = queryEndpoint
    ): Flow<RdfResult> = getSparqlResult(query, endpoint) {
        it.receive<ByteReadChannel>().getQueryResults(
            bufferSize = readXmlBufferSize
        )
    }

    private fun createBindingSet(bindings: MapBindingSet.(vf: ValueFactory) -> Unit): MapBindingSet {
        val bindingSet = MapBindingSet()
        bindings(bindingSet, valueFactory)
        return bindingSet
    }

    private suspend fun <T> getSparqlResult(
        query: String,
        endpoint: String,
        mapper: suspend (HttpResponse) -> T
    ): T {
        val response = client.submitForm<HttpResponse>(endpoint,
            formParameters = Parameters.build {
                append("query", query)
            }) {
            setHeaders()
        }
        if (HttpStatusCode.OK != response.call.response.status) {
            throw handleNotOkResponse(response)
        } else {
            return mapper(response)
        }
    }

    private suspend fun handleNotOkResponse(
        response: HttpResponse
    ): Exception {
        val status = response.call.response.status
        val content = response.call.response.readText()
        val error = try {
            jackson.readValue<ErrorResponse>(content)
        } catch (e: Exception) {
            return HttpException(content, status)
        }
        return QueryException(error, status)
    }

    private fun HttpRequestBuilder.setHeaders() {
        header(HttpHeaders.Accept, "application/sparql-results+xml")
    }

    internal suspend fun getQueryResponseAsString(query: String): String {
        return client.get("$queryEndpoint?query=$query") {
            setHeaders()
        }
    }

    internal suspend fun getString(url: String): String {
        return client.get(url) {
            setHeaders()
        }
    }

    override fun close() {
        client.close()
    }

}

data class RdfResult(val bindingNames: List<String>, val bindingSet: BindingSet)
