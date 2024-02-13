DROP TABLE IF EXISTS transacoes;
DROP TABLE IF EXISTS clientes;

CREATE TABLE clientes (
	id BIGSERIAL PRIMARY KEY,
	nome VARCHAR(50) NOT NULL,
	limite NUMERIC NOT NULL,
	saldo NUMERIC NOT NULL DEFAULT 0,
	CONSTRAINT saldo_nao_negativo check (saldo >= (limite * -1))--o que me permite usar ON CONFLICT
);

CREATE TABLE transacoes (
	id BIGSERIAL PRIMARY KEY,
	cliente_id BIGINT NOT NULL,
	valor NUMERIC NOT NULL,
	tipo BOOLEAN NOT NULL,--1 se D, 0 se C
	descricao VARCHAR(10) NOT NULL,
	realizada_em TIMESTAMP NOT NULL DEFAULT NOW(),
	CONSTRAINT fk_clientes_transacoes_id
		FOREIGN KEY (cliente_id) REFERENCES clientes(id)
);


--talvez criar uma view materialized para as 10 ultimas transações...



DO $$
BEGIN
  INSERT INTO clientes (nome, limite)
  VALUES
    ('o barato sai caro', 1000 * 100),
    ('zan corp ltda', 800 * 100),
    ('les cruders', 10000 * 100),
    ('padaria joia de cocaia', 100000 * 100),
    ('kid mais', 5000 * 100);
END; $$
