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

    private val data = StringBuilder()

    override suspend fun add(statements: Iterable<Statement>) {
        statements.appendModifyDataCommand(data, ModifyCommand.INSERT)
        data.append("; ")
    }

    override suspend fun remove(statements: Iterable<Statement>) {
        statements.appendModifyDataCommand(data, ModifyCommand.DELETE)
        data.append("; ")
    }

    override suspend fun commit() {
        client.getSparqlResult(data.toString(), client.config.updateUrl, "update") {}
    }

    override suspend fun rollback() {
        data.clear()
    }
}

class StardogTransaction(private val id: UUID, private val client: KSparqlClient) : Transaction {
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