package br.com.meli.order.application.port.out;

import br.com.meli.order.domain.order.Order;

public interface OutboxPort {

    // SOLUCAO (Bloco 3 — Outbox pattern): grava o evento dentro da mesma transacao
    // do PostgreSQL que grava o pedido. Se a transacao confirmar, o evento esta
    // garantido no banco — independente do estado do Elasticsearch.
    void save(String aggregateId, String eventType, Order order);
}

