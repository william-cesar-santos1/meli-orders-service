package br.com.meli.orders.application;

import br.com.meli.orders.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.domain.Order;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListOrdersByCustomerUseCase {

    private final OrderRepositoryPort orderRepository;

    public ListOrdersByCustomerUseCase(OrderRepositoryPort orderRepository) {
        this.orderRepository = orderRepository;
    }

    // PROBLEMA (N+1): o adapter JPA usa LAZY loading para os itens do pedido.
    // Cada pedido retornado dispara uma query adicional ao banco para carregar os itens.
    // Com 100 pedidos: 101 queries por requisição. O problema está no adapter —
    // a porta (interface) permanece a mesma. Somente a implementação muda no Bloco 2.
    public List<Order> execute(String customerId) {
        return orderRepository.findByCustomerId(customerId);
    }
}

