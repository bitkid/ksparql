package com.bitkid.ksparql

import com.bitkid.ksparql.test.TransactionCalls
import org.apache.jena.fuseki.embedded.FusekiServer
import org.apache.jena.query.DatasetFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class LocalTransactionTest {

    private val databaseName = "test"
    private val server = FusekiServer.create()
        .setPort(0)
        .add("/$databaseName", DatasetFactory.createTxnMem()).build()
        .apply {
            start()
        }

    private val localFusekiConfig = ClientConfig(
        databaseHost = "http://localhost",
        databasePort = server.port,
        databaseName = databaseName,
        transactionType = TransactionType.LOCAL
    )

    private val client = KSparqlClient(localFusekiConfig)

    private val calls = TransactionCalls()

    @AfterEach
    fun close() {
        client.close()
        server.stop()
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