package br.com.meli.orders.application;

import br.com.meli.orders.application.port.out.OrderRepositoryPort;
import br.com.meli.orders.domain.Coupon;
import br.com.meli.orders.domain.Money;
import br.com.meli.orders.domain.Order;
import br.com.meli.orders.domain.exceptions.OrderNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ApplyCouponUseCase {

    private final OrderRepositoryPort orderRepository;

    public ApplyCouponUseCase(OrderRepositoryPort orderRepository) {
        this.orderRepository = orderRepository;
    }

    // SOLUÇÃO: cada cupom calcula o desconto sobre o total CORRENTE — não sobre o original.
    // O segundo cupom desconta sobre o valor já reduzido pelo primeiro.
    // A composição de descontos é correta para qualquer número de cupons.
    public Money execute(Long orderId, List<Coupon> coupons) {
        if (coupons == null) {
            throw new IllegalArgumentException("Lista de cupons não pode ser nula");
        }
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
        Money total = new Money(order.totalAmount());
        for (Coupon coupon : coupons) {
            total = total.subtract(coupon.calculateDiscount(total)); // SOLUÇÃO: total corrente
        }
        return total;
    }
}
