package com.natwest.kata.bank.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

record TransferRequest(@NotBlank String fromAccountId, @NotBlank String toAccountId, @NotNull BigDecimal amount) {
}
