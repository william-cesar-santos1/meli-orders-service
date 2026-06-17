package br.com.meli.order.application.port.out;

import br.com.meli.order.domain.order.Order;

public interface OrderIndexPort {

    // PROBLEMA: indexação direta no Elasticsearch sem garantia de atomicidade.
    // Se o Elasticsearch falhar após o PostgreSQL confirmar, o pedido existe
    // na fonte de verdade mas não no índice de busca — inconsistência silenciosa.
    void index(Order order);
}

