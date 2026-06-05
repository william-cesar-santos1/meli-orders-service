package br.com.meli.orders.application.port.out;

import br.com.meli.orders.domain.Order;
import br.com.meli.orders.domain.OrderStatus;

import java.util.List;
import java.util.Optional;

public interface OrderRepositoryPort {

    Order save(Order order);

    Order updateStatus(Long id, OrderStatus status);

    Optional<Order> findById(Long id);

    List<Order> findByCustomerId(String customerId);
}

