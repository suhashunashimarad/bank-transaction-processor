package com.natwest.kata.bank.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Account {

    private final String id;
    private BigDecimal balance = new BigDecimal("0.00");
    private final List<LedgerEntry> ledger = new ArrayList<>();

    public Account(String id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public String id() {
        return id;
    }

    public synchronized BigDecimal balance() {
        return balance;
    }

    public synchronized List<LedgerEntry> ledger() {
        return List.copyOf(ledger);
    }

    public synchronized LedgerEntry deposit(Amount amount, Instant at) {
        return credit(UUID.randomUUID(), TransactionType.DEPOSIT, amount, null, at);
    }

    public synchronized LedgerEntry withdraw(Amount amount, Instant at) {
        return debit(UUID.randomUUID(), TransactionType.WITHDRAWAL, amount, null, at);
    }

    public synchronized LedgerEntry transferOut(UUID transferId, Amount amount, String recipientId, Instant at) {
        return debit(transferId, TransactionType.TRANSFER_OUT, amount, recipientId, at);
    }

    public synchronized LedgerEntry transferIn(UUID transferId, Amount amount, String senderId, Instant at) {
        return credit(transferId, TransactionType.TRANSFER_IN, amount, senderId, at);
    }

    private LedgerEntry credit(UUID transactionId, TransactionType type, Amount amount, String counterpartyId, Instant at) {
        return record(transactionId, type, amount, balance.add(amount.value()), counterpartyId, at);
    }

    private LedgerEntry debit(UUID transactionId, TransactionType type, Amount amount, String counterpartyId, Instant at) {
        if (balance.compareTo(amount.value()) < 0) {
            throw new InsufficientFundsException(id, amount, balance);
        }
        return record(transactionId, type, amount, balance.subtract(amount.value()), counterpartyId, at);
    }

    private LedgerEntry record(UUID transactionId, TransactionType type, Amount amount,
                               BigDecimal newBalance, String counterpartyId, Instant at) {
        LedgerEntry entry = new LedgerEntry(transactionId, type, amount, newBalance, counterpartyId, at);
        balance = newBalance;
        ledger.add(entry);
        return entry;
    }
}
