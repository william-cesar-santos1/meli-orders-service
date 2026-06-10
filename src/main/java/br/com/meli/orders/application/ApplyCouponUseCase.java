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

    // PROBLEMA: os descontos de todos os cupons são calculados sobre o total ORIGINAL.
    // Com dois cupons combinados (ex: CategoryCoupon + MinValueCoupon), ambos descontam
    // sobre o mesmo valor base — o desconto total excede o esperado.
    // Exatamente o bug que causou R$ 1,8 M de prejuízo na Promofy em produção.
    public Money execute(Long orderId, List<Coupon> coupons) {
        if (coupons == null) {
            throw new IllegalArgumentException("Lista de cupons não pode ser nula");
        }
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
        Money original = new Money(order.totalAmount());
        Money totalDiscount = Money.ZERO;
        for (Coupon coupon : coupons) {
            totalDiscount = totalDiscount.add(coupon.calculateDiscount(original)); // BUG: sempre sobre original
        }
        return original.subtract(totalDiscount);
    }
}

