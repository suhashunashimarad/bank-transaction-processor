package com.natwest.kata.bank.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private static final Instant NOW = Instant.parse("2026-04-20T09:00:00Z");

    private final Account account = new Account("ACC-1", "alice");

    @Test
    void a_new_account_has_a_zero_balance_and_an_empty_ledger() {
        assertThat(account.balance()).isEqualByComparingTo("0");
        assertThat(account.ledger()).isEmpty();
    }

    @Test
    void is_owned_only_by_the_customer_it_was_opened_for() {
        assertThat(account.isOwnedBy("alice")).isTrue();
        assertThat(account.isOwnedBy("bob")).isFalse();
    }

    @Nested
    class Deposit {

        @Test
        void increases_the_balance() {
            account.deposit(Amount.of("100.00"), NOW);

            assertThat(account.balance()).isEqualByComparingTo("100.00");
        }

        @Test
        void is_recorded_in_the_ledger_with_its_timestamp_and_resulting_balance() {
            LedgerEntry entry = account.deposit(Amount.of("100.00"), NOW);

            assertThat(entry.transactionId()).isNotNull();
            assertThat(entry.type()).isEqualTo(TransactionType.DEPOSIT);
            assertThat(entry.amount()).isEqualTo(Amount.of("100.00"));
            assertThat(entry.balanceAfter()).isEqualByComparingTo("100.00");
            assertThat(entry.timestamp()).isEqualTo(NOW);
            assertThat(entry.counterpartyAccountId()).isNull();
            assertThat(account.ledger()).containsExactly(entry);
        }
    }

    @Nested
    class Withdrawal {

        @Test
        void decreases_the_balance() {
            account.deposit(Amount.of("100.00"), NOW);

            account.withdraw(Amount.of("30.00"), NOW);

            assertThat(account.balance()).isEqualByComparingTo("70.00");
        }

        @Test
        void may_take_the_balance_to_exactly_zero() {
            account.deposit(Amount.of("100.00"), NOW);

            account.withdraw(Amount.of("100.00"), NOW);

            assertThat(account.balance()).isEqualByComparingTo("0");
        }

        @Test
        void is_recorded_in_the_ledger() {
            account.deposit(Amount.of("100.00"), NOW);

            LedgerEntry entry = account.withdraw(Amount.of("30.00"), NOW);

            assertThat(entry.type()).isEqualTo(TransactionType.WITHDRAWAL);
            assertThat(entry.balanceAfter()).isEqualByComparingTo("70.00");
            assertThat(account.ledger()).last().isEqualTo(entry);
        }

        @Test
        void that_would_overdraw_the_account_is_rejected_and_leaves_no_trace() {
            account.deposit(Amount.of("50.00"), NOW);

            assertThatThrownBy(() -> account.withdraw(Amount.of("50.01"), NOW))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessageContaining("ACC-1");

            assertThat(account.balance()).isEqualByComparingTo("50.00");
            assertThat(account.ledger()).hasSize(1);
        }
    }

    @Nested
    class Transfer {

        private final UUID transferId = UUID.randomUUID();

        @Test
        void out_debits_the_account_and_records_the_recipient() {
            account.deposit(Amount.of("100.00"), NOW);

            LedgerEntry entry = account.transferOut(transferId, Amount.of("40.00"), "ACC-2", NOW);

            assertThat(account.balance()).isEqualByComparingTo("60.00");
            assertThat(entry.transactionId()).isEqualTo(transferId);
            assertThat(entry.type()).isEqualTo(TransactionType.TRANSFER_OUT);
            assertThat(entry.counterpartyAccountId()).isEqualTo("ACC-2");
        }

        @Test
        void out_cannot_overdraw_the_account() {
            assertThatThrownBy(() -> account.transferOut(transferId, Amount.of("0.01"), "ACC-2", NOW))
                    .isInstanceOf(InsufficientFundsException.class);

            assertThat(account.ledger()).isEmpty();
        }

        @Test
        void in_credits_the_account_and_records_the_sender() {
            LedgerEntry entry = account.transferIn(transferId, Amount.of("40.00"), "ACC-2", NOW);

            assertThat(account.balance()).isEqualByComparingTo("40.00");
            assertThat(entry.transactionId()).isEqualTo(transferId);
            assertThat(entry.type()).isEqualTo(TransactionType.TRANSFER_IN);
            assertThat(entry.counterpartyAccountId()).isEqualTo("ACC-2");
        }
    }

    @Test
    void the_ledger_lists_transactions_oldest_first() {
        LedgerEntry first = account.deposit(Amount.of("10.00"), NOW);
        LedgerEntry second = account.withdraw(Amount.of("5.00"), NOW.plusSeconds(1));

        assertThat(account.ledger()).containsExactly(first, second);
    }

    @Test
    void the_ledger_cannot_be_modified_from_outside() {
        account.deposit(Amount.of("10.00"), NOW);

        assertThatThrownBy(() -> account.ledger().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void the_balance_is_always_reported_in_pence() {
        assertThat(account.balance()).isEqualTo(new BigDecimal("0.00"));
    }
}
