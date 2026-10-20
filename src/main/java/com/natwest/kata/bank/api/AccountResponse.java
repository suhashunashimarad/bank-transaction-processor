package com.natwest.kata.bank.api;

import java.math.BigDecimal;

record AccountResponse(String accountId, BigDecimal balance) {
}
