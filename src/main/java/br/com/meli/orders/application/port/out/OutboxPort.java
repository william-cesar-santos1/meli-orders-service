package br.com.meli.orders.application.port.out;

import java.util.UUID;

public interface OutboxPort {

    // SOLUCAO (Bloco 3 — Outbox pattern): grava o evento dentro da mesma transacao
    // do PostgreSQL que grava o pedido. Se a transacao confirmar, o evento esta
    // garantido no banco — independente do estado do Elasticsearch.
    void save(String aggregateId, String eventType, String payload);
}

