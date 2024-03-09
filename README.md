API para participação da rinha de backend 2021 q1: https://github.com/zanfranceschi/rinha-de-backend-2024-q1/

Usando:
- PostgreSQL
- Ktor

A parte mais importantes para cuidar que nenhum cliente ficará com saldo negativo, foi deixar uma restrição na tabela do banco de dados.
```
CREATE TABLE clientes (
	id BIGSERIAL PRIMARY KEY,
	limite NUMERIC NOT NULL,
	saldo NUMERIC NOT NULL DEFAULT 0,
	CONSTRAINT saldo_nao_negativo check (saldo >= (limite * -1))--o que me permite usar ON CONFLICT
);
```
```
WITH create_transacao AS (
    INSERT INTO transacoes (cliente_id, valor, tipo, descricao) VALUES (?, ?, ?, ?)
) UPDATE clientes
  SET saldo = saldo + ?
  WHERE id = ?
  RETURNING limite, saldo
```

Criar build da imagem da aplicação:
` docker build -t rinha .`

Rodar a aplicação:
`docker compose --compatibility up`

Monitorar:
`docker stats`

Version 1
![Screenshot from 2024-03-09 16-11-07](https://github.com/LucasDelboni/rinha-de-backend-2024-q1-grupo-d2/assets/4420675/d341f900-0620-4d5c-963e-05662f480c45)

