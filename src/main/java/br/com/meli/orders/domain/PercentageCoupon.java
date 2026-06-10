package br.com.meli.orders.domain;

import java.math.BigDecimal;

// Cupom percentual: aplica percentual de desconto sobre o total
public record PercentageCoupon(BigDecimal discountRate) implements Coupon {

    @Override
    public Money calculateDiscount(Money total) {
        return total.multiply(discountRate);
    }
}

