package br.com.meli.orders.order.application.port.out;

import br.com.meli.orders.order.domain.Order;

public interface OrderIndexPort {

    // PROBLEMA: indexação direta no Elasticsearch sem garantia de atomicidade.
    // Se o Elasticsearch falhar após o PostgreSQL confirmar, o pedido existe
    // na fonte de verdade mas não no índice de busca — inconsistência silenciosa.
    void index(Order order);
}

