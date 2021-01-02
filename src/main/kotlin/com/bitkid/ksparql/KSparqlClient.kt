package com.bitkid.ksparql

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
import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.query.impl.MapBindingSet
import org.eclipse.rdf4j.repository.sparql.query.QueryStringUtil

data class ClientConfig(
    val databaseHost: String,
    val databasePort: Int,
    val databaseName: String,
    val user: String = "",
    val password: String = "",
    val queryEndpoint: String = "query",
    val updateEndpoint: String = "update",
    val readXmlBufferSize: Int = 1024 * 1024,
    val databaseBasePath: String = "$databaseHost:$databasePort/$databaseName",
    val updateUrl: String = "$databaseBasePath/$updateEndpoint",
    val queryUrl: String = "$databaseBasePath/$queryEndpoint",
    val transactionBaseUrl: String = "$databaseBasePath/transaction",
    val transactionType: TransactionType = TransactionType.STARDOG
)

open class KSparqlException(message: String) : RuntimeException(message)

class HttpRequestException(message: String, val httpStatusCode: HttpStatusCode) : KSparqlException(message)

class KSparqlClient(
    internal val config: ClientConfig
) : AutoCloseable {
    companion object {
        const val XML_ACCEPT_HEADER = "application/sparql-results+xml"
    }

    private val valueFactory = SimpleValueFactory.getInstance()

    private val client = HttpClient(Apache) {
        expectSuccess = false
        install(Auth) {
            basic {
                username = config.user
                password = config.password
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

    suspend fun clear(vararg contexts: Resource?) {
        return getSparqlResult(createClearString(contexts), config.updateUrl, "update") {}
    }

    suspend fun add(statements: Iterable<Statement>, vararg contexts: Resource) {
        val insertString = statements.createInsertDataCommand(*contexts)
        return getSparqlResult(insertString, config.updateUrl, "update") {}
    }

    override fun close() {
        client.close()
    }

    suspend fun begin(reasoning: Boolean = false): Transaction {
        return config.transactionType.create(this, reasoning)
    }

    suspend fun transaction(
        reasoning: Boolean = false,
        block: suspend Transaction.() -> Unit
    ) {
        val transaction = begin(reasoning)
        try {
            block(transaction)
            transaction.commit()
        } catch (e: Exception) {
            transaction.rollback()
            throw e
        }
    }

    internal suspend fun postAndCheck(url: String, bodyContent: String = ""): String {
        val response = client.post<HttpResponse>(url) {
            body = bodyContent
        }
        checkResponse(response)
        return response.receive()
    }

    internal suspend fun getBooleanResult(
        query: String,
        endpoint: String = config.queryUrl
    ) = getSparqlResult(query, endpoint) {
        it.receive<ByteReadChannel>().getBooleanResult(
            bufferSize = config.readXmlBufferSize
        )
    }

    internal suspend fun getQueryResult(
        query: String,
        endpoint: String = config.queryUrl
    ): Flow<RdfResult> = getSparqlResult(query, endpoint) {
        it.receive<ByteReadChannel>().getQueryResults(
            bufferSize = config.readXmlBufferSize
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
        parameterName: String = "query",
        mapper: suspend (HttpResponse) -> T
    ): T {
        val response = client.submitForm<HttpResponse>(endpoint,
            formParameters = Parameters.build {
                append(parameterName, query)
            }) {
            header(HttpHeaders.Accept, XML_ACCEPT_HEADER)
        }
        checkResponse(response)
        return mapper(response)
    }

    private suspend fun checkResponse(response: HttpResponse) {
        if (HttpStatusCode.OK != response.call.response.status) {
            throw handleNotOkResponse(response)
        }
    }

    private suspend fun handleNotOkResponse(
        response: HttpResponse
    ): Exception {
        val status = response.call.response.status
        val content = response.call.response.readText()
        return if (content.isBlank())
            HttpRequestException("Server returned status $status", status)
        else
            HttpRequestException(content, status)
    }
}

data class RdfResult(val bindingNames: List<String>, val bindingSet: BindingSet)
