package com.natwest.kata.bank.domain;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String accountId, Amount requested, BigDecimal available) {
        super("Account %s has insufficient funds: requested %s, available %s"
                .formatted(accountId, requested.value().toPlainString(), available.toPlainString()));
    }
}
