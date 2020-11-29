package com.bitkid.ksparql

import com.fasterxml.aalto.AsyncByteArrayFeeder
import com.fasterxml.aalto.AsyncXMLStreamReader
import com.fasterxml.aalto.stax.InputFactoryImpl
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.xml.stream.XMLStreamConstants

data class XmlEvent(val event: Int, val data: AsyncXMLStreamReader<AsyncByteArrayFeeder>)

class Client : AutoCloseable {
    private val client = HttpClient(Apache)

    suspend fun getXml(url: String, bufferSize: Int = 1024 * 100): Flow<XmlEvent> {
        return flow {
            val channel = client.get<HttpResponse>(url).receive<ByteReadChannel>()
            val byteBuffer = ByteArray(bufferSize)
            val parser = InputFactoryImpl().createAsyncFor(ByteArray(0))

            do {
                val currentRead = channel.readAvailable(byteBuffer, 0, bufferSize)
                parser.inputFeeder.feedInput(byteBuffer, 0, currentRead)

                while (parser.hasNext()) {
                    val value = parser.next()
                    if (value == AsyncXMLStreamReader.EVENT_INCOMPLETE)
                        break
                    else {
                        if (value == XMLStreamConstants.CHARACTERS && parser.isWhiteSpace) {
                            // not sure, ignore for now
                        } else {
                            emit(XmlEvent(value, parser))
                        }
                    }
                }
            } while (currentRead >= 0)
        }
    }

    override fun close() {
        client.close()
    }

}