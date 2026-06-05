package br.com.meli.orders.application;

import br.com.meli.orders.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.domain.Order;
import br.com.meli.orders.domain.OrderStatus;
import br.com.meli.orders.domain.exceptions.OrderNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class PayOrderUseCase {

    private final OrderRepositoryPort orderRepository;

    public PayOrderUseCase(OrderRepositoryPort orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order execute(Long orderId) {
        orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return orderRepository.updateStatus(orderId, OrderStatus.PAID);
    }
}
