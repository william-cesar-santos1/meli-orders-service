package br.com.meli.order.application;

import br.com.meli.order.application.port.out.OrderSearchPort;
import br.com.meli.order.domain.order.Order;

import java.util.List;

/**
 * Caso de uso: busca full-text de pedidos via Elasticsearch.
 * POJO puro — sem anotações de framework.
 */
public class SearchOrdersUseCase {

    private final OrderSearchPort searchPort;

    public SearchOrdersUseCase(OrderSearchPort searchPort) {
        this.searchPort = searchPort;
    }

    public List<Order> search(String query) {
        return searchPort.search(query);
    }
}
