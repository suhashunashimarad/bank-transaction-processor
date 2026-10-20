package com.natwest.kata.bank.service;

import com.natwest.kata.bank.domain.Amount;

import java.time.Instant;
import java.util.UUID;

public record Transfer(UUID transactionId, String fromAccountId, String toAccountId, Amount amount, Instant timestamp) {
}
