package com.bitkid.ksparql.stardog

import com.bitkid.ksparql.ClientConfig
import com.bitkid.ksparql.KSparqlClient
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Disabled
@Execution(ExecutionMode.SAME_THREAD)
class TransactionsTest {

    private val client = KSparqlClient(
        ClientConfig(
            databaseHost = "http://localhost",
            databasePort = 5820,
            databaseName = "test",
            user = "admin",
            password = "admin"
        )
    )

    private val calls = TransactionCalls()

    @AfterEach
    fun close() {
        runBlocking {
            client.clear()
        }
        client.close()
    }

    @Test
    fun `can add in transaction`() {
        calls.canAddInTransaction(client)
    }

    @Test
    fun `can remove in transaction`() {
        calls.canRemoveInTransaction(client)
    }

    @Test
    fun `can add with transaction closure`() {
        calls.canAddWithTransactionClosure(client)
    }

    @Test
    fun `can remove with transaction closure`() {
        calls.canRemoveWithTransactionClosure(client)
    }
}