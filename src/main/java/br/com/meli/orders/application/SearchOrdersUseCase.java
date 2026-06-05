package br.com.meli.orders.application;

import br.com.meli.orders.application.port.out.OrderSearchPort;
import br.com.meli.orders.domain.Order;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SearchOrdersUseCase {

    private final OrderSearchPort searchPort;

    public SearchOrdersUseCase(OrderSearchPort searchPort) {
        this.searchPort = searchPort;
    }

    // PROBLEMA: a implementação atual do adapter usa SQL LIKE '%q%' no PostgreSQL,
    // forçando full table scan. Com 1 milhão de pedidos, a busca degrada linearmente.
    // Além disso, LIKE não faz stemming: 'tênis' não encontra 'tenis' ou 'Tênis'.
    // A porta (interface) é a mesma — somente a implementação do adapter muda no Bloco 3.
    public List<Order> search(String query) {
        return searchPort.search(query);
    }
}
