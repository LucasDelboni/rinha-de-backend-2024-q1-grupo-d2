package com.rinha.plugins

import com.rinha.serilizer.KZonedDateTimeSerializer
import kotlinx.coroutines.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.sql.Connection
import java.time.ZoneId
import java.time.ZonedDateTime

enum class TipoDeTransacao(val value: Boolean) {
    d(true), c(false);

    companion object {
        private val map = entries.associateBy { it.value }
        infix fun from(value: Boolean) = map[value]!!
    }
}
@Serializable
data class Transacao(val valor: Int, val tipo: TipoDeTransacao, val descricao: String, val realizada_em: @Serializable( with = KZonedDateTimeSerializer::class) ZonedDateTime)
@Serializable
data class Saldo(val limite: Int, val saldo: Int)
class RinhaService(private val connection: Connection) {
    companion object {
        private val CLEINTE_EXISTS = """SELECT EXISTS(SELECT 1 FROM clientes WHERE id = ?)"""
        private const val INSERT_TRANSACAO = """INSERT INTO transacoes (cliente_id, valor, tipo, descricao) VALUES (?, ?, ?, ?)"""
        private const val SELECT_SALDO = """SELECT saldo, limite FROM clientes WHERE id = ?"""
        private const val SELECT_LAST_TRANSACOES = """SELECT valor, tipo, descricao, realizada_em FROM transacoes WHERE cliente_id = ? ORDER BY id DESC LIMIT 10"""
        private const val UPDATE_NEW_SALDO = """
            UPDATE clientes
            SET saldo = saldo + ?
            WHERE id = ?
            RETURNING limite, saldo
        """
        private const val CREATE_TRANSACAO = """
            WITH create_transacao AS (
                $INSERT_TRANSACAO
            ) $UPDATE_NEW_SALDO
        """
    }

    //init {
    //    val statement = connection.createStatement()
    //    statement.executeUpdate(DROP_TRANSACAO)
    //    statement.executeUpdate(DROP_CLIENTES)
    //    statement.executeUpdate(CREATE_TABLE_CLIENTES)
    //    statement.executeUpdate(CREATE_TABLE_TRANSACOES)
    //    statement.executeUpdate(INSERT_CLIENTES)
    //}

    suspend fun getExtrato(clienteId: Long): ExtratoResponse {
        val saldo = getSaldo(clienteId)
        val last10Transacoes = getLast10Transacoes(clienteId)

        return ExtratoResponse(
            SaldoExtratoResponse(
                limite =  saldo.limite,
                data_extrato = ZonedDateTime.now(),
                total = saldo.saldo
            ),
            ultimas_transacoes = last10Transacoes,
        )
    }

    // Read a city
    suspend fun getSaldo(clienteId: Long): Saldo = withContext(Dispatchers.IO) {
        val statement = connection.prepareStatement(SELECT_SALDO)
        statement.setLong(1, clienteId)
        val resultSet = statement.executeQuery()

        if (resultSet.next()) {
            return@withContext Saldo(
                limite = resultSet.getInt("limite"),
                saldo = resultSet.getInt("saldo"),
            )
        } else {
            throw Exception("Record not found")
        }
    }

    suspend fun getLast10Transacoes(clienteId: Long): MutableList<Transacao> = withContext(Dispatchers.IO) {
        val statement = connection.prepareStatement(SELECT_LAST_TRANSACOES)
        statement.setLong(1, clienteId)
        val resultSet = statement.executeQuery()

        val transacoes = mutableListOf<Transacao>()
        while (resultSet.next()) {
            transacoes.add(Transacao(
                valor = resultSet.getInt("valor"),
                tipo = TipoDeTransacao.from(resultSet.getBoolean("tipo")),
                descricao = resultSet.getString("descricao"),
                realizada_em = resultSet.getTimestamp("realizada_em").toInstant().atZone(ZoneId.of("UTC")),
            ))
        }
        return@withContext transacoes
    }

    fun getNewSaldo(clienteId: Long, transacao: TransacaoRequest): Saldo {
        val valor = transacao.valor.toInt()
        val amount = when (TipoDeTransacao.valueOf(transacao.tipo)) {
            TipoDeTransacao.c -> valor
            TipoDeTransacao.d -> valor * -1
        }
        val statement = connection.prepareStatement(CREATE_TRANSACAO)
        statement.setLong(1, clienteId)
        statement.setInt(2, valor)
        statement.setBoolean(3, TipoDeTransacao.valueOf(transacao.tipo).value)
        statement.setString(4, transacao.descricao)
        statement.setInt(5, amount)
        statement.setLong(6, clienteId)
        statement.executeQuery()



        val resultSet = statement.resultSet
        resultSet.next()
        return Saldo(
            limite = resultSet.getInt("limite"),
            saldo = resultSet.getInt("saldo"),
        )
    }

    fun clienteExists(clienteId: Long): Boolean {
        val statement = connection.prepareStatement(CLEINTE_EXISTS)
        statement.setLong(1, clienteId)
        statement.executeQuery()
        val resultSet = statement.resultSet
        resultSet.next()
        return resultSet.getBoolean(1)
    }
}
