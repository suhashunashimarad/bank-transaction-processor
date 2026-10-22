package com.natwest.kata.bank.api;

import com.natwest.kata.bank.domain.Account;
import com.natwest.kata.bank.domain.Amount;
import com.natwest.kata.bank.service.BankingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/accounts")
class AccountController {

    private final BankingService bank;

    AccountController(BankingService bank) {
        this.bank = bank;
    }

    @PostMapping
    ResponseEntity<AccountResponse> openAccount() {
        Account account = bank.openAccount();
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(account.id()).toUri();
        return ResponseEntity.created(location).body(new AccountResponse(account.id(), account.balance()));
    }

    @GetMapping("/{accountId}")
    AccountResponse balance(@PathVariable String accountId) {
        return new AccountResponse(accountId, bank.balanceOf(accountId));
    }

    @GetMapping("/{accountId}/transactions")
    List<TransactionResponse> transactions(@PathVariable String accountId) {
        return bank.historyOf(accountId).stream().map(TransactionResponse::from).toList();
    }

    @PostMapping("/{accountId}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    TransactionResponse deposit(@PathVariable String accountId, @Valid @RequestBody MoneyRequest request) {
        return TransactionResponse.from(bank.deposit(accountId, new Amount(request.amount())));
    }

    @PostMapping("/{accountId}/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    TransactionResponse withdraw(@PathVariable String accountId, @Valid @RequestBody MoneyRequest request) {
        return TransactionResponse.from(bank.withdraw(accountId, new Amount(request.amount())));
    }
}
