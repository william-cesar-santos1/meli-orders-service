package br.com.meli.orders.domain;

import java.math.BigDecimal;

// Cupom de valor mínimo: desconto fixo aplicado apenas se o total atingir o minValue
public record MinValueCoupon(BigDecimal minValue, BigDecimal discountAmount) implements Coupon {

    @Override
    public Money calculateDiscount(Money total) {
        if (total.amount().compareTo(minValue) >= 0) {
            return new Money(discountAmount);
        }
        return Money.ZERO;
    }
}

