package com.natwest.kata.bank.domain;

import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntry(
        UUID transactionId,
        TransactionType type,
        Amount amount,
        BigDecimal balanceAfter,
        @Nullable String counterpartyAccountId,
        Instant timestamp) {
}
