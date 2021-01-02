package com.bitkid.ksparql.test

import com.bitkid.ksparql.KSparqlClient
import com.bitkid.ksparql.iri
import com.bitkid.ksparql.model
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

class TransactionCalls {

    private val model = TestUtils.testEntity.model()
        .add(iri("http://propi"), "bla")
        .add(iri("http://propi1"), 5)
        .build()

    fun canAddInTransaction(kSparqlClient: KSparqlClient) = runBlocking<Unit> {
        val tr = kSparqlClient.begin()
        tr.add(model)
        tr.rollback()
        expectThat(kSparqlClient.query(TestUtils.fetchAllQuery).toList()).hasSize(0)

        val tc = kSparqlClient.begin()
        tc.add(model)
        expectThat(kSparqlClient.query(TestUtils.fetchAllQuery).toList()).hasSize(0)
        tc.commit()
        expectThat(kSparqlClient.query(TestUtils.fetchAllQuery).toList()).hasSize(2)
    }

    fun canRemoveInTransaction(kSparqlClient: KSparqlClient) = runBlocking<Unit> {
        val ta = kSparqlClient.begin()
        ta.add(model)
        ta.commit()
        expectThat(kSparqlClient.query(TestUtils.fetchAllQuery).toList()).hasSize(2)

        val tr = kSparqlClient.begin()
        tr.remove(TestUtils.testEntity.model().add(iri("http://propi"), "bla").build())
        expectThat(kSparqlClient.query(TestUtils.fetchAllQuery).toList()).hasSize(2)
        tr.commit()
        expectThat(kSparqlClient.query(TestUtils.fetchAllQuery).toList()).hasSize(1)
    }

    fun canAddWithTransactionClosure(kSparqlClient: KSparqlClient) = runBlocking<Unit> {
        expectThrows<RuntimeException> {
            kSparqlClient.transaction {
                add(model)
                throw RuntimeException("bla")
            }
        }.get { message }.isEqualTo("bla")
        expectThat(kSparqlClient.query(TestUtils.fetchAllQuery).toList()).hasSize(0)

        kSparqlClient.transaction {
            add(model)
        }
        expectThat(kSparqlClient.query(TestUtils.fetchAllQuery).toList()).hasSize(2)
    }

    fun canRemoveWithTransactionClosure(kSparqlClient: KSparqlClient) = runBlocking<Unit> {
        kSparqlClient.transaction {
            add(model)
        }
        expectThat(kSparqlClient.query(TestUtils.fetchAllQuery).toList()).hasSize(2)
        kSparqlClient.transaction {
            remove(TestUtils.testEntity.model().add(iri("http://propi"), "bla").build())
        }
        expectThat(kSparqlClient.query(TestUtils.fetchAllQuery).toList()).hasSize(1)
    }
}