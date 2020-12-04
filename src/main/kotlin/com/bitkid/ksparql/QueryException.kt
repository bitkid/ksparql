package com.bitkid.ksparql

import io.ktor.http.*

data class ErrorResponse(val message: String, val code: String)

class QueryException(val errorResponse: ErrorResponse, val httpStatusCode: HttpStatusCode) :
    Exception("$httpStatusCode -> query failed (${errorResponse.code}): ${errorResponse.message}")