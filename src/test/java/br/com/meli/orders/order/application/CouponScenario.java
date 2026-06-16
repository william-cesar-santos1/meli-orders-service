package br.com.meli.orders.order.application;

import br.com.meli.orders.shared.domain.CategoryCoupon;
import br.com.meli.orders.shared.domain.Coupon;
import br.com.meli.orders.shared.domain.MinValueCoupon;
import br.com.meli.orders.shared.domain.Money;
import br.com.meli.orders.shared.domain.PercentageCoupon;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public record CouponScenario(String description, Money orderTotal,
                              List<Coupon> coupons, Money expectedTotal) {

    public static CouponScenario parse(String csvLine) {
        String[] parts = csvLine.split(",", 4);
        String description = parts[0].trim();
        Money orderTotal = new Money(new BigDecimal(parts[1].trim()));
        List<Coupon> coupons = Arrays.stream(parts[2].trim().split("\\|"))
            .map(CouponScenario::parseCoupon)
            .toList();
        Money expectedTotal = new Money(new BigDecimal(parts[3].trim()));
        return new CouponScenario(description, orderTotal, coupons, expectedTotal);
    }

    private static Coupon parseCoupon(String token) {
        String[] f = token.split(":");
        return switch (f[0].trim()) {
            case "CATEGORY"   -> new CategoryCoupon(f[1].trim(), new BigDecimal(f[2].trim()));
            case "MIN_VALUE"  -> new MinValueCoupon(new BigDecimal(f[1].trim()), new BigDecimal(f[2].trim()));
            case "PERCENTAGE" -> new PercentageCoupon(new BigDecimal(f[1].trim()));
            default -> throw new IllegalArgumentException("Tipo de cupom desconhecido: " + f[0]);
        };
    }
}
