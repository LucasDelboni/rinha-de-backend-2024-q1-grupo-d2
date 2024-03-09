package com.rinha.plugins

import com.rinha.serilizer.KZonedDateTimeSerializer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.sql.Connection
import java.time.ZonedDateTime

fun Application.configureRouting() {
    val dbConnection: Connection = connectToPostgres(embedded = false)
    val service = RinhaService(dbConnection)

    routing {
        get("/") {
            call.respond(HttpStatusCode.OK, "hello world")
        }
        get("/clientes/{id}/extrato") {
            try{
                val clienteId = call.parameters["id"]?.toLong()
                clienteId?.let {
                    if (!service.clienteExists(clienteId)) {
                        call.respond(HttpStatusCode.NotFound)
                    }
                    val extrato = service.getExtrato(clienteId)
                    call.respond(HttpStatusCode.OK, extrato)
                } ?: call.respond(HttpStatusCode.UnprocessableEntity)
            } catch (cause: IllegalArgumentException) {
                call.respond(HttpStatusCode.UnprocessableEntity)
            }
        }
        post("/clientes/{id}/transacoes") {
            val clienteId = call.parameters["id"]?.toLong()
            clienteId?.let {
                val request = call.receive<TransacaoRequest>()
                if (request.descricao.isNullOrBlank() || request.descricao.length > 10 || request.valor.toIntOrNull() == null) {
                    call.respond(HttpStatusCode.UnprocessableEntity)
                }
                if (!service.clienteExists(clienteId)) {
                    call.respond(HttpStatusCode.NotFound)
                }
                try {
                    val newSaldo = service.getNewSaldo(clienteId, request)
                    call.respond(HttpStatusCode.OK, SaldoResponse(newSaldo.limite, newSaldo.saldo))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.UnprocessableEntity)
                }
            } ?: call.respond(HttpStatusCode.UnprocessableEntity)
        }
    }
}

@Serializable
class TransacaoRequest(val valor: String, val tipo: String, val descricao: String?)
@Serializable
class SaldoResponse(val limite: Int, val saldo: Int)
@Serializable
data class SaldoExtratoResponse(val total: Int, val data_extrato: @Serializable( with = KZonedDateTimeSerializer::class) ZonedDateTime, val limite: Int)
@Serializable
class ExtratoResponse(
    val saldo: SaldoExtratoResponse,
    val ultimas_transacoes: MutableList<Transacao>
)