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
import org.eclipse.rdf4j.model.IRI
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
    val user: String,
    val password: String,
    val queryEndpoint: String = "query",
    val updateEndpoint: String = "update",
    val readXmlBufferSize: Int = 1024 * 1024,
    val databaseBasePath: String = "$databaseHost:$databasePort/$databaseName",
    val updateUrl: String = "$databaseBasePath/$updateEndpoint",
    val queryUrl: String = "$databaseBasePath/$queryEndpoint",
    val transactionBaseUrl: String = "$databaseBasePath/transaction"
)

class Transaction(val id: UUID, private val client: KSparqlClient) {
    suspend fun add(
        statements: Iterable<Statement>,
        endpoint: String = "${client.config.databaseBasePath}/$id/add",
    ) {
        client.addInTransaction(statements, endpoint)
    }
}

class KSparqlClient(
    internal val config: ClientConfig
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

    suspend fun add(
        statements: Iterable<Statement>,
        endpoint: String = config.updateUrl,
        vararg contexts: Resource
    ) {
        val insertString = statements.createInsertDataCommand(*contexts)
        return getSparqlResult(insertString, endpoint) {}
    }

    internal suspend fun addInTransaction(
        statements: Iterable<Statement>,
        endpoint: String,
    ) {
        val insertBody = buildString {
            statements.createDataBody(this, true)
        }
        val response = client.post<HttpResponse>(endpoint) {
            body = insertBody
        }
        checkResponse(response)
    }

    suspend fun clear(endpoint: String = config.updateUrl, vararg contexts: Resource?) {
        val clearString = if (contexts.isEmpty()) {
            "CLEAR ALL"
        } else {
            buildString {
                for (context in contexts) {
                    when (context) {
                        null -> {
                            append("CLEAR ALL DEFAULT; ")
                        }
                        is IRI -> {
                            append("CLEAR ALL GRAPH <" + context.stringValue() + ">; ")
                        }
                        else -> {
                            throw RuntimeException("SPARQL does not support named graphs identified by blank nodes.")
                        }
                    }
                }
            }
        }
        return getSparqlResult(clearString, endpoint) {}
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
        mapper: suspend (HttpResponse) -> T
    ): T {
        val response = client.submitForm<HttpResponse>(endpoint,
            formParameters = Parameters.build {
                append("query", query)
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
            return HttpException(content, status)
        }
        return QueryException(error, status)
    }

    override fun close() {
        client.close()
    }

    suspend fun transaction(function: suspend Transaction.() -> Unit) {
        val transaction = begin()
        try {
            function(transaction)
            commit(transaction)
        } catch (e: Exception) {
            rollback(transaction)
            throw e
        }
    }


}

data class RdfResult(val bindingNames: List<String>, val bindingSet: BindingSet)
