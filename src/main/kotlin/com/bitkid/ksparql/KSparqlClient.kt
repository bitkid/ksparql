package com.bitkid.ksparql

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
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
import org.eclipse.rdf4j.query.impl.MapBindingSet
import org.eclipse.rdf4j.repository.sparql.query.QueryStringUtil

class KSparqlClient(
    private val queryEndpoint: String,
    userName: String = "admin",
    pw: String = "admin",
    private val readXmlBufferSize: Int = 1024 * 100,
) : AutoCloseable {

    private val jackson = jacksonObjectMapper()
    private val valueFactory = SimpleValueFactory.getInstance()
    private val client = HttpClient(Apache) {
        expectSuccess = false
        install(Auth) {
            basic {
                username = userName
                password = pw
            }
        }
    }

    suspend fun tupleQuery(
        query: String,
        bindings: MapBindingSet.(vf: ValueFactory) -> Unit = {}
    ): Flow<RdfResult> {
        val bindingSet = MapBindingSet()
        bindings(bindingSet, valueFactory)
        val queryString = QueryStringUtil.getTupleQueryString(query, bindingSet)
        return getRdfResults(queryString)
    }

    internal suspend fun getRdfResults(
        query: String,
        endpoint: String = queryEndpoint
    ): Flow<RdfResult> {
        val response = client.submitForm<HttpResponse>(endpoint,
            formParameters = Parameters.build {
                append("query", query)
            }) {
            setHeaders()
        }
        if (HttpStatusCode.OK != response.call.response.status) {
            throw handleNotOkResponse(response)
        } else {
            return response.receive<ByteReadChannel>().getData(
                bufferSize = readXmlBufferSize
            )
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
        return client.get("$queryEndpoint/query?query=$query") {
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

