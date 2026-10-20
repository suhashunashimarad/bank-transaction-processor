package com.natwest.kata.bank.service;

import com.natwest.kata.bank.domain.Account;
import com.natwest.kata.bank.domain.AccountNotFoundException;
import com.natwest.kata.bank.domain.AccountRepository;
import com.natwest.kata.bank.domain.Amount;
import com.natwest.kata.bank.domain.InvalidTransferException;
import com.natwest.kata.bank.domain.LedgerEntry;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BankingService {

    private final AccountRepository accounts;
    private final Clock clock;

    public BankingService(AccountRepository accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    public Account openAccount() {
        return accounts.save(new Account(UUID.randomUUID().toString()));
    }

    public BigDecimal balanceOf(String accountId) {
        return find(accountId).balance();
    }

    public List<LedgerEntry> historyOf(String accountId) {
        return find(accountId).ledger();
    }

    public LedgerEntry deposit(String accountId, Amount amount) {
        return find(accountId).deposit(amount, clock.instant());
    }

    public LedgerEntry withdraw(String accountId, Amount amount) {
        return find(accountId).withdraw(amount, clock.instant());
    }

    public Transfer transfer(String fromAccountId, String toAccountId, Amount amount) {
        if (fromAccountId.equals(toAccountId)) {
            throw new InvalidTransferException("Cannot transfer from account %s to itself".formatted(fromAccountId));
        }
        Account from = find(fromAccountId);
        Account to = find(toAccountId);
        Account firstLock = from.id().compareTo(to.id()) < 0 ? from : to;
        Account secondLock = firstLock == from ? to : from;

        UUID transferId = UUID.randomUUID();
        Instant now = clock.instant();
        synchronized (firstLock) {
            synchronized (secondLock) {
                from.transferOut(transferId, amount, to.id(), now);
                to.transferIn(transferId, amount, from.id(), now);
            }
        }
        return new Transfer(transferId, from.id(), to.id(), amount, now);
    }

    private Account find(String accountId) {
        return accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    }
}
