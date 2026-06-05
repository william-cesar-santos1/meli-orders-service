package br.com.meli.orders.application;

import br.com.meli.orders.domain.Order;
import br.com.meli.orders.infrastructure.jpa.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SearchOrdersUseCase {

    private final OrderRepository orderRepository;

    public SearchOrdersUseCase(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    // PROBLEMA: LIKE '%q%' força um full table scan no PostgreSQL.
    // Sem índice de texto, cada busca percorre todos os registros da tabela.
    // Com 1 milhão de pedidos, a busca degrada linearmente.
    // Além disso, LIKE não faz stemming: 'tênis' não encontra 'tenis' ou 'Tênis'.
    public List<Order> search(String query) {
        return orderRepository.findByProductDescriptionContaining(query)
                .stream()
                .map(entity -> entity.toDomain())
                .toList();
    }
}

