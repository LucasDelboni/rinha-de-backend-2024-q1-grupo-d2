package com.rinha

import com.rinha.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.*

class ApplicationTest {
//    @Test
//    fun testExtrato() = testApplication {
//        application {
//            configureRouting()
//        }
//        createClient {
//            this@testApplication.install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
//                json()
//            }
//        }
//        val client = createClient {
//            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
//               json(Json {
//                    prettyPrint = true
//                    isLenient = true
//                })
//            }
//        }
//        client.get("/clientes/1/extrato").apply {
//            assertEquals(HttpStatusCode.OK, status)
//            assertEquals("Hello World!", bodyAsText())
//        }
//    }
//
    @Test
    fun createTransacao() = testApplication {
        application {
            configureRouting()
            configureSerialization()
        }

        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                })
            }
        }
        client.post("/clientes/1/transacoes" ){
            contentType(ContentType.Application.Json)
            setBody(TransacaoRequest(10, "c", "decricao"))
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("Hello World!", bodyAsText())
        }
    }
}
