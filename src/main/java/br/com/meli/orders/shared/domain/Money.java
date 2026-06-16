package br.com.meli.orders.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal amount) {

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money {
        Objects.requireNonNull(amount, "amount cannot be null");
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    public Money subtract(Money other) {
        BigDecimal result = this.amount.subtract(other.amount);
        return result.compareTo(BigDecimal.ZERO) < 0 ? ZERO : new Money(result);
    }

    public Money multiply(BigDecimal factor) {
        return new Money(this.amount.multiply(factor));
    }
}

