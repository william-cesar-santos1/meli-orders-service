package br.com.meli.orders.order.application;

import br.com.meli.orders.order.application.port.out.FindOrderPort;
import br.com.meli.orders.order.domain.Order;
import br.com.meli.orders.order.domain.exceptions.OrderNotFoundException;
import br.com.meli.orders.shared.domain.Coupon;
import br.com.meli.orders.shared.domain.Money;

import java.util.List;

/**
 * Caso de uso: calcular desconto acumulado de cupons em um pedido.
 * POJO puro — sem anotações de framework.
 */
public class ApplyCouponUseCase {

    private final FindOrderPort findOrderPort;

    public ApplyCouponUseCase(FindOrderPort findOrderPort) {
        this.findOrderPort = findOrderPort;
    }

    public Money execute(Long orderId, List<Coupon> coupons) {
        if (coupons == null) {
            throw new IllegalArgumentException("Lista de cupons não pode ser nula");
        }
        Order order = findOrderPort.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        Money total = new Money(order.totalAmount());
        for (Coupon coupon : coupons) {
            total = total.subtract(coupon.calculateDiscount(total));
        }
        return total;
    }
}
