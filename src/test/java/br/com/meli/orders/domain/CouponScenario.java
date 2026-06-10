package br.com.meli.orders.domain;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

// SOLUÇÃO: record imutável que representa um cenário lido do CSV.
// Formato: description,order_total,coupon_types,expected_total
// coupon_types: TYPE:param1:param2 separados por | para múltiplos cupons
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

