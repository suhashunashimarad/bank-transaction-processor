package com.natwest.kata.bank.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A strictly positive monetary amount in pounds and pence.
 * <p>
 * Every operation moves an {@code Amount}, so "invalid amount" is impossible to express
 * once one has been constructed. Fractions of a penny are rejected rather than rounded:
 * silently changing how much money moves is worse than refusing the request.
 */
public record Amount(BigDecimal value) {

    private static final int PENCE_SCALE = 2;

    public Amount {
        if (value == null) {
            throw new InvalidAmountException("Amount is required");
        }
        if (value.signum() <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero but was " + value.toPlainString());
        }
        if (value.stripTrailingZeros().scale() > PENCE_SCALE) {
            throw new InvalidAmountException("Amount must have at most 2 decimal places but was " + value.toPlainString());
        }
        value = value.setScale(PENCE_SCALE, RoundingMode.UNNECESSARY);
    }

    public static Amount of(String value) {
        return new Amount(new BigDecimal(value));
    }
}
