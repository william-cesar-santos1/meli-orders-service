package br.com.meli.orders.order.application;

import br.com.meli.orders.order.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.order.domain.Order;

import java.util.List;

/**
 * Caso de uso: listar pedidos de um cliente.
 * POJO puro — sem anotações de framework.
 */
public class ListOrdersByCustomerUseCase {

    private final OrderRepositoryPort orderRepository;

    public ListOrdersByCustomerUseCase(OrderRepositoryPort orderRepository) {
        this.orderRepository = orderRepository;
    }

    public List<Order> execute(String customerId) {
        return orderRepository.findByCustomerId(customerId);
    }
}
