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

class Client(private val bufferSize: Int = 1024 * 100) : AutoCloseable {
    private val client = HttpClient(Apache)

    suspend fun getXml(url: String): Flow<XmlEvent> {
        return flow {
            val channel = client.get<HttpResponse>(url).receive<ByteReadChannel>()
            val byteBuffer = ByteArray(bufferSize)
            val parser = InputFactoryImpl().createAsyncFor(ByteArray(0))

            do {
                val currentRead = channel.readAvailable(byteBuffer, 0, bufferSize)
                parser.inputFeeder.feedInput(byteBuffer, 0, currentRead)

                while (parser.hasNext()) {
                    val event = parser.next()
                    if (event == AsyncXMLStreamReader.EVENT_INCOMPLETE)
                        break
                    // not sure, ignore for now
                    if (event == XMLStreamConstants.CHARACTERS && parser.isWhiteSpace)
                        continue
                    emit(XmlEvent(event, parser))
                }
            } while (currentRead >= 0)
        }
    }

    override fun close() {
        client.close()
    }

}