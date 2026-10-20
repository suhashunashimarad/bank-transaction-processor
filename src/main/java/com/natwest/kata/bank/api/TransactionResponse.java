package com.natwest.kata.bank.api;

import com.natwest.kata.bank.domain.LedgerEntry;
import com.natwest.kata.bank.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record TransactionResponse(
        UUID transactionId,
        TransactionType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String counterpartyAccountId,
        Instant timestamp) {

    static TransactionResponse from(LedgerEntry entry) {
        return new TransactionResponse(entry.transactionId(), entry.type(), entry.amount().value(),
                entry.balanceAfter(), entry.counterpartyAccountId(), entry.timestamp());
    }
}
