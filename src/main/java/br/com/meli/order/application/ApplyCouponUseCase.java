package br.com.meli.order.application;

import br.com.meli.order.application.port.out.FindOrderPort;
import br.com.meli.order.domain.coupon.Coupon;
import br.com.meli.order.domain.coupon.Money;
import br.com.meli.order.domain.exceptions.OrderNotFoundException;
import br.com.meli.order.domain.order.Order;

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
