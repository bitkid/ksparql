package com.bitkid.ksparql

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class TestClient {

    private val client = HttpClient(Apache) {
        expectSuccess = false
    }

    suspend fun getRemoteService(): Flow<Int> {
        val response = client.get<HttpResponse>("some url")
        if (response.status != HttpStatusCode.OK) {
            // do some stuff with the response and then
            throw RuntimeException()
        }
        val bytes = response.receive<ByteReadChannel>()
        return flow {
            val buffer = ByteArray(1024)
            do {
                val read = bytes.readAvailable(buffer)
                emit(doStuffWithBytes(buffer))
            } while (read >= 0)
        }
    }

    private fun doStuffWithBytes(byteArray: ByteArray): Int {
        return 1
    }
}