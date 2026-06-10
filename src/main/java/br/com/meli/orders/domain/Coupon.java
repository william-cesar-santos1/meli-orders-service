package br.com.meli.orders.domain;

public sealed interface Coupon permits CategoryCoupon, MinValueCoupon, PercentageCoupon {
    // calcula o desconto a partir do total informado
    Money calculateDiscount(Money total);
}

