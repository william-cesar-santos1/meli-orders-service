package br.com.meli.orders.application;

import br.com.meli.orders.domain.Order;
import br.com.meli.orders.domain.OrderStatus;
import br.com.meli.orders.domain.exceptions.OrderNotFoundException;
import br.com.meli.orders.infrastructure.jpa.OrderEntity;
import br.com.meli.orders.infrastructure.jpa.OrderRepository;
import org.springframework.stereotype.Service;

@Service
public class PayOrderUseCase {

    private final OrderRepository orderRepository;

    public PayOrderUseCase(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order execute(Long orderId) {
        OrderEntity entity = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        entity.setStatus(OrderStatus.PAID.name());
        OrderEntity saved = orderRepository.save(entity);
        return saved.toDomain();
    }
}

