package com.bitkid.ksparql

import com.bitkid.ksparql.test.TestUtils.expectResultsForStardogXml
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.File

class XmlToFlowTest {
    private val xmlBytes = File(XmlToFlowTest::class.java.getResource("/stardog.xml").toURI()).readBytes()

    @Test
    fun `can get flow for xml byte array`() {
        runBlocking {
            val results = xmlBytes.getData().toList()
            expectResultsForStardogXml(results.map { it.bindingSet })
        }
    }
}