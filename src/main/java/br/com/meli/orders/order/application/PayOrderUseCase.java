package br.com.meli.orders.order.application;

import br.com.meli.orders.order.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.order.domain.Order;
import br.com.meli.orders.order.domain.OrderStatus;
import br.com.meli.orders.order.domain.exceptions.OrderNotFoundException;

/**
 * Caso de uso: marcar pedido como pago manualmente (endpoint de pagamento direto).
 * POJO puro — sem anotações de framework.
 */
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
