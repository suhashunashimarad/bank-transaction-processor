package com.natwest.kata.bank.api;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Only presence is checked here; what makes an amount valid is the domain's {@code Amount} rule.
 */
record MoneyRequest(@NotNull BigDecimal amount) {
}
