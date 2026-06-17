package br.com.meli.order.application;

import br.com.meli.order.application.port.out.FindOrderPort;
import br.com.meli.order.application.port.out.SaveOrderPort;
import br.com.meli.order.domain.exceptions.OrderNotFoundException;
import br.com.meli.order.domain.order.Order;
import br.com.meli.order.domain.order.OrderStatus;

/**
 * Caso de uso: marcar pedido como pago manualmente (endpoint de pagamento direto).
 * POJO puro — sem anotações de framework.
 */
public class PayOrderUseCase {

    private final FindOrderPort findOrderPort;
    private final SaveOrderPort saveOrderPort;

    public PayOrderUseCase(
            FindOrderPort findOrderPort,
            SaveOrderPort saveOrderPort
    ) {
        this.findOrderPort = findOrderPort;
        this.saveOrderPort = saveOrderPort;
    }

    public Order execute(Long orderId) {
        findOrderPort.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return saveOrderPort.updateStatus(orderId, OrderStatus.PAID);
    }
}
