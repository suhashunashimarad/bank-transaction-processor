package com.natwest.kata.bank.domain;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String accountId) {
        super("Account %s does not exist".formatted(accountId));
    }
}
