package com.bitkid.ksparql.test

import com.bitkid.ksparql.KSparqlClient
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.auth.*
import io.ktor.client.features.auth.providers.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*

class DataFetcher {
    private val client = HttpClient(Apache) {
        expectSuccess = false
        install(Auth) {
            basic {
                username = "admin"
                password = "admin"
            }
        }
    }

    internal suspend fun getQueryResponseAsString(endpoint: String, query: String): String {
        val response = client.submitForm<HttpResponse>(endpoint,
            formParameters = Parameters.build {
                append("query", query)
            }) {
            header(HttpHeaders.Accept, KSparqlClient.XML_ACCEPT_HEADER)
        }
        return response.receive()
    }

    internal suspend fun getString(endpoint: String): String {
        return client.get(endpoint)
    }
}