package com.natwest.kata.bank.security;

import com.natwest.kata.bank.domain.AccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("accountAccess")
public class AccountAccess {

    private final AccountRepository accounts;

    AccountAccess(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public boolean isOwner(Authentication authentication, String accountId) {
        return accounts.findById(accountId)
                .map(account -> account.isOwnedBy(authentication.getName()))
                .orElse(true);
    }
}
