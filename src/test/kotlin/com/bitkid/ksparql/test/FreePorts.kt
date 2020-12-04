package com.bitkid.ksparql.test

import java.net.ServerSocket
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.concurrent.thread

/**
 * This was shamelessly stolen from the ktor test suite
 */
object FreePorts {

    private const val CAPACITY = 20
    private const val CAPACITY_LOW = 10

    private val found = Collections.synchronizedSet(HashSet<Int>())
    private val free = Collections.synchronizedList(LinkedList<Int>())

    init {
        allocate(CAPACITY)
    }

    fun select(): Int {
        if (free.size < CAPACITY_LOW) {
            thread(name = "free-port-population") {
                allocate(CAPACITY - free.size)
            }
        }

        while (true) {
            try {
                return free.removeAt(0)
            } catch (expected: IndexOutOfBoundsException) {
                // may happen if concurrently removed
                allocate(CAPACITY)
            }
        }
    }

    fun recycle(port: Int) {
        if (port in found && checkFreePort(port)) {
            free.add(port)
        }
    }

    private fun allocate(count: Int) {
        if (count <= 0) return
        val sockets = ArrayList<ServerSocket>()

        try {
            for (repeat in 1..count) {
                try {
                    val socket = ServerSocket(0, 1)
                    sockets.add(socket)
                } catch (ignore: Throwable) {
                    Thread.sleep(1000)
                }
            }
        } finally {
            sockets.removeAll {
                try {
                    it.close()
                    !found.add(it.localPort)
                } catch (ignore: Throwable) {
                    true
                }
            }

            Thread.sleep(1000)

            sockets.forEach {
                free.add(it.localPort)
            }
        }
    }

    private fun checkFreePort(port: Int): Boolean {
        return try {
            ServerSocket(port).close()
            true
        } catch (unableToBind: Throwable) {
            false
        }
    }
}