package com.bitkid.ksparql

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.auth.*
import io.ktor.client.features.auth.providers.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow

class KSparqlClient(
    private val databaseUrl: String,
    private val bufferSize: Int = 1024 * 100,
    userName: String = "admin",
    pw: String = "admin"
) : AutoCloseable {

    private val jackson = jacksonObjectMapper()

    private val client = HttpClient(Apache) {
        expectSuccess = false

        install(Auth) {
            basic {
                username = userName
                password = pw
            }
        }
    }

    suspend fun getRdfResults(
        query: String,
        path: String = "/query"
    ): Flow<RdfResult> {
        val response = client.get<HttpResponse>("$databaseUrl$path?query=$query") {
            setHeaders()
        }
        if (HttpStatusCode.OK != response.call.response.status) {
            throw handleNotOkResponse(response)
        } else {
            return response.receive<ByteReadChannel>().getData(
                bufferSize = bufferSize
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
        return client.get("$databaseUrl/query?query=$query") {
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

