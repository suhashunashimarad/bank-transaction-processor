package com.natwest.kata.bank.domain;

import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An immutable record of one movement of money on one account.
 * <p>
 * Both legs of a transfer share the same {@code transactionId}, which lets the two
 * accounts' ledgers be reconciled against each other.
 *
 * @param counterpartyAccountId the other account in a transfer; {@code null} for deposits and withdrawals
 */
public record LedgerEntry(
        UUID transactionId,
        TransactionType type,
        Amount amount,
        BigDecimal balanceAfter,
        @Nullable String counterpartyAccountId,
        Instant timestamp) {
}
