package br.com.meli.order.application.port.out;

import br.com.meli.order.domain.order.Order;

// SOLUÇÃO: porta de saida para publicacao de eventos de dominio.
// Unifica a responsabilidade de publicacao — o caso de uso nao precisa
// conhecer se os eventos vao para um broker, para o outbox ou para um log.
// Principio: Ports and Adapters — o adaptador escolhe o mecanismo de publicacao.
public interface PublishEventPort {
    // Publica o evento de criacao de pedido (via outbox ou broker direto)
    void publishOrderCreated(Order order);
}

