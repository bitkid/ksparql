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
import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.query.impl.MapBindingSet
import org.eclipse.rdf4j.repository.sparql.query.QueryStringUtil
import java.util.*

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
    val transactionBaseUrl: String = "$databaseBasePath/transaction"
)

class Transaction(val id: UUID, private val client: KSparqlClient) {
    suspend fun add(statements: Iterable<Statement>) {
        client.addInTransaction(statements, id)
    }
}

class KSparqlClient(
    private val config: ClientConfig
) : AutoCloseable {
    companion object {
        const val XML_ACCEPT_HEADER = "application/sparql-results+xml"
    }

    private val jackson = jacksonObjectMapper()
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
        val response = client.post<HttpResponse>("${config.transactionBaseUrl}/begin?reasoning=$reasoning")
        checkResponse(response)
        return Transaction(UUID.fromString(response.receive()), this)
    }

    suspend fun commit(transaction: Transaction) {
        val response = client.post<HttpResponse>("${config.transactionBaseUrl}/commit/${transaction.id}")
        checkResponse(response)
    }

    suspend fun rollback(transaction: Transaction) {
        val response = client.post<HttpResponse>("${config.transactionBaseUrl}/rollback/${transaction.id}")
        checkResponse(response)
    }

    suspend fun transaction(
        reasoning: Boolean = false,
        block: suspend Transaction.() -> Unit
    ) {
        val transaction = begin(reasoning)
        try {
            block(transaction)
            commit(transaction)
        } catch (e: Exception) {
            rollback(transaction)
            throw e
        }
    }

    internal suspend fun addInTransaction(
        statements: Iterable<Statement>,
        id: UUID
    ) {
        val insertBody = buildString {
            statements.createDataBody(this, true)
        }
        val response = client.post<HttpResponse>("${config.databaseBasePath}/$id/add") {
            body = insertBody
        }
        checkResponse(response)
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
        val error = try {
            jackson.readValue<ErrorResponse>(content)
        } catch (e: Exception) {
            return if (content.isBlank())
                HttpException("Server returned status $status", status)
            else
                HttpException(content, status)
        }
        return QueryException(error, status)
    }
}

data class RdfResult(val bindingNames: List<String>, val bindingSet: BindingSet)
