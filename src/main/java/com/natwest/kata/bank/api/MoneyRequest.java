package com.natwest.kata.bank.api;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

record MoneyRequest(@NotNull BigDecimal amount) {
}
