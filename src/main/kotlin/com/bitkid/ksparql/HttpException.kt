package com.bitkid.ksparql

import io.ktor.http.*

class HttpException(message: String, val httpStatusCode: HttpStatusCode) : Exception(message)