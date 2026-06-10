package br.com.meli.orders.domain;

import java.math.BigDecimal;

// Cupom de categoria: aplica percentual de desconto sobre o total do pedido
public record CategoryCoupon(String category, BigDecimal discountRate) implements Coupon {

    @Override
    public Money calculateDiscount(Money total) {
        return total.multiply(discountRate);
    }
}

