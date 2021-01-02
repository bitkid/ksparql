package com.bitkid.ksparql

import org.eclipse.rdf4j.model.Statement
import java.util.*

interface Transaction {
    suspend fun add(statements: Iterable<Statement>)
    suspend fun remove(statements: Iterable<Statement>)
    suspend fun commit()
    suspend fun rollback()
}

class LocalTransaction(private val client: KSparqlClient) : Transaction {
    override suspend fun add(statements: Iterable<Statement>) {
        TODO("Not yet implemented")
    }

    override suspend fun remove(statements: Iterable<Statement>) {
        TODO("Not yet implemented")
    }

    override suspend fun commit() {
        TODO("Not yet implemented")
    }

    override suspend fun rollback() {
        TODO("Not yet implemented")
    }
}

class StardogTransaction(val id: UUID, private val client: KSparqlClient) : Transaction {
    override suspend fun add(statements: Iterable<Statement>) {
        client.postAndCheck("${client.config.databaseBasePath}/$id/add", statements.asString())
    }

    override suspend fun remove(statements: Iterable<Statement>) {
        client.postAndCheck("${client.config.databaseBasePath}/$id/remove", statements.asString())
    }

    override suspend fun commit() {
        client.postAndCheck("${client.config.transactionBaseUrl}/commit/$id")
    }

    override suspend fun rollback() {
        client.postAndCheck("${client.config.transactionBaseUrl}/rollback/$id")
    }
}

enum class TransactionType {
    STARDOG, LOCAL;

    suspend fun create(client: KSparqlClient, reasoning: Boolean = false): Transaction {
        return when (this) {
            STARDOG -> {
                val response = client.postAndCheck("${client.config.transactionBaseUrl}/begin?reasoning=$reasoning")
                StardogTransaction(UUID.fromString(response), client)
            }
            LOCAL -> {
                LocalTransaction(client)
            }
        }
    }
}