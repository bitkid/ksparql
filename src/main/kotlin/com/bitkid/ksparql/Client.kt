package com.bitkid.ksparql

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.auth.*
import io.ktor.client.features.auth.providers.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow

class Client(
    private val bufferSize: Int = 1024 * 100,
    userName: String = "admin",
    pw: String = "admin"
) : AutoCloseable {
    private val client = HttpClient(Apache) {
        install(Auth) {
            basic {
                username = userName
                password = pw
            }
        }
    }

    suspend fun getRdfXml(url: String): Flow<RdfResult> {
        val channel = client.get<HttpResponse>(url).receive<ByteReadChannel>()
        return QueryResultToFlow().getData(channel, bufferSize = bufferSize)
    }

    override fun close() {
        client.close()
    }

    suspend fun getString(url: String): String {
        return client.get(url)
    }

}