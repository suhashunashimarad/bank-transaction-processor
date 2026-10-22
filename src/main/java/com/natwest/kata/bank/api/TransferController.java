package com.natwest.kata.bank.api;

import com.natwest.kata.bank.domain.Amount;
import com.natwest.kata.bank.service.BankingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
class TransferController {

    private final BankingService bank;

    TransferController(BankingService bank) {
        this.bank = bank;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@accountAccess.isOwner(authentication, #request.fromAccountId())")
    TransferResponse transfer(@Valid @RequestBody TransferRequest request) {
        return TransferResponse.from(
                bank.transfer(request.fromAccountId(), request.toAccountId(), new Amount(request.amount())));
    }
}
