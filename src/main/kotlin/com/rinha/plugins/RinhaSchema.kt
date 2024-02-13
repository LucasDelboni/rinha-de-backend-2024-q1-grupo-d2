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
data class Cliente(val id: Long, val nome: String, val limite: Int, val saldo: Int = 0)
@Serializable
data class Transacao(val valor: Int, val tipo: TipoDeTransacao, val descricao: String, val realizada_em: @Serializable( with = KZonedDateTimeSerializer::class) ZonedDateTime)
@Serializable
data class Saldo(val limite: Int, val saldo: Int)
class RinhaService(private val connection: Connection) {
    companion object {
        private const val DROP_CLIENTES = "DROP TABLE IF EXISTS clientes;"
        private const val DROP_TRANSACAO = "DROP TABLE IF EXISTS transacoes;"
        private const val CREATE_TABLE_CLIENTES = """
            CREATE TABLE clientes (
                id BIGSERIAL PRIMARY KEY,
                nome VARCHAR(50) NOT NULL,--50 é o suficiente?
                limite NUMERIC NOT NULL,
                saldo NUMERIC NOT NULL DEFAULT 0,
                CONSTRAINT saldo_nao_negativo check (saldo < limite)
            );
        """
        private const val CREATE_TABLE_TRANSACOES = """
            CREATE TABLE transacoes (
                id BIGSERIAL PRIMARY KEY,
                cliente_id BIGINT NOT NULL,
                valor NUMERIC NOT NULL,
                tipo BOOLEAN NOT NULL,--1 se D, 0 se C
                descricao VARCHAR(10) NOT NULL,
                realizada_em TIMESTAMP NOT NULL DEFAULT NOW(),
                CONSTRAINT fk_clientes_transacoes_id FOREIGN KEY (cliente_id) REFERENCES clientes(id)
            );
        """
        private const val INSERT_CLIENTES = """
            INSERT INTO clientes (nome, limite)
              VALUES
                ('o barato sai caro', 1000 * 100),
                ('zan corp ltda', 800 * 100),
                ('les cruders', 10000 * 100),
                ('padaria joia de cocaia', 100000 * 100),
                ('kid mais', 5000 * 100);
        """
        private val CLEINTE_EXISTS = """SELECT EXISTS(SELECT 1 FROM clientes WHERE id = ?)"""
        private const val INSERT_TRANSACAO = """INSERT INTO transacoes (cliente_id, valor, tipo, descricao) VALUES (?, ?, ?, ?)"""
        private val SELECT_SALDO = """SELECT saldo, limite FROM clientes WHERE id = ?"""
        private val SELECT_LAST_TRANSACOES = """SELECT valor, tipo, descricao, realizada_em FROM transacoes WHERE cliente_id = ? ORDER BY id DESC LIMIT 10"""
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
        val amount = when (TipoDeTransacao.valueOf(transacao.tipo)) {
            TipoDeTransacao.c -> transacao.valor
            TipoDeTransacao.d -> transacao.valor * -1
        }
        val statement = connection.prepareStatement(CREATE_TRANSACAO)
        statement.setLong(1, clienteId)
        statement.setInt(2, transacao.valor)
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
