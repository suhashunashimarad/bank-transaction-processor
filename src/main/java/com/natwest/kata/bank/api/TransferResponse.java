package com.natwest.kata.bank.api;

import com.natwest.kata.bank.service.Transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record TransferResponse(UUID transactionId, String fromAccountId, String toAccountId, BigDecimal amount, Instant timestamp) {

    static TransferResponse from(Transfer transfer) {
        return new TransferResponse(transfer.transactionId(), transfer.fromAccountId(), transfer.toAccountId(),
                transfer.amount().value(), transfer.timestamp());
    }
}
