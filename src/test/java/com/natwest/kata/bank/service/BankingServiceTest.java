package com.natwest.kata.bank.service;

import com.natwest.kata.bank.domain.Account;
import com.natwest.kata.bank.domain.AccountNotFoundException;
import com.natwest.kata.bank.domain.Amount;
import com.natwest.kata.bank.domain.InsufficientFundsException;
import com.natwest.kata.bank.domain.InvalidTransferException;
import com.natwest.kata.bank.domain.LedgerEntry;
import com.natwest.kata.bank.domain.TransactionType;
import com.natwest.kata.bank.infrastructure.InMemoryAccountRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BankingServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-20T09:00:00Z");

    private final BankingService bank = new BankingService(
            new InMemoryAccountRepository(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Nested
    class OpeningAnAccount {

        @Test
        void gives_each_account_a_unique_id() {
            Account first = bank.openAccount();
            Account second = bank.openAccount();

            assertThat(first.id()).isNotEqualTo(second.id());
        }

        @Test
        void starts_with_a_zero_balance() {
            Account account = bank.openAccount();

            assertThat(bank.balanceOf(account.id())).isEqualByComparingTo("0");
        }
    }

    @Nested
    class DepositsAndWithdrawals {

        @Test
        void are_timestamped_with_the_current_time() {
            String id = bank.openAccount().id();

            LedgerEntry entry = bank.deposit(id, Amount.of("25.00"));

            assertThat(entry.timestamp()).isEqualTo(NOW);
        }

        @Test
        void are_reflected_in_balance_and_history() {
            String id = bank.openAccount().id();

            bank.deposit(id, Amount.of("100.00"));
            bank.withdraw(id, Amount.of("40.00"));

            assertThat(bank.balanceOf(id)).isEqualByComparingTo("60.00");
            assertThat(bank.historyOf(id))
                    .extracting(LedgerEntry::type)
                    .containsExactly(TransactionType.DEPOSIT, TransactionType.WITHDRAWAL);
        }

        @Test
        void cannot_overdraw_an_account() {
            String id = bank.openAccount().id();

            assertThatThrownBy(() -> bank.withdraw(id, Amount.of("0.01")))
                    .isInstanceOf(InsufficientFundsException.class);
        }
    }

    @Nested
    class Transfers {

        @Test
        void move_money_from_one_account_to_another() {
            String alice = accountWithBalance("100.00");
            String bob = accountWithBalance("10.00");

            bank.transfer(alice, bob, Amount.of("30.00"));

            assertThat(bank.balanceOf(alice)).isEqualByComparingTo("70.00");
            assertThat(bank.balanceOf(bob)).isEqualByComparingTo("40.00");
        }

        @Test
        void appear_in_both_ledgers_under_one_transaction_id() {
            String alice = accountWithBalance("100.00");
            String bob = bank.openAccount().id();

            Transfer transfer = bank.transfer(alice, bob, Amount.of("30.00"));

            LedgerEntry debit = bank.historyOf(alice).getLast();
            LedgerEntry credit = bank.historyOf(bob).getLast();
            assertThat(debit.transactionId()).isEqualTo(transfer.transactionId());
            assertThat(credit.transactionId()).isEqualTo(transfer.transactionId());
            assertThat(debit.type()).isEqualTo(TransactionType.TRANSFER_OUT);
            assertThat(debit.counterpartyAccountId()).isEqualTo(bob);
            assertThat(credit.type()).isEqualTo(TransactionType.TRANSFER_IN);
            assertThat(credit.counterpartyAccountId()).isEqualTo(alice);
        }

        @Test
        void that_would_overdraw_the_sender_change_neither_account() {
            String alice = accountWithBalance("10.00");
            String bob = bank.openAccount().id();

            assertThatThrownBy(() -> bank.transfer(alice, bob, Amount.of("10.01")))
                    .isInstanceOf(InsufficientFundsException.class);

            assertThat(bank.balanceOf(alice)).isEqualByComparingTo("10.00");
            assertThat(bank.historyOf(bob)).isEmpty();
        }

        @Test
        void to_the_same_account_are_rejected() {
            String alice = accountWithBalance("10.00");

            assertThatThrownBy(() -> bank.transfer(alice, alice, Amount.of("1.00")))
                    .isInstanceOf(InvalidTransferException.class);

            assertThat(bank.historyOf(alice)).hasSize(1);
        }

        @Test
        void to_an_unknown_account_are_rejected_without_debiting_the_sender() {
            String alice = accountWithBalance("10.00");

            assertThatThrownBy(() -> bank.transfer(alice, "does-not-exist", Amount.of("1.00")))
                    .isInstanceOf(AccountNotFoundException.class);

            assertThat(bank.balanceOf(alice)).isEqualByComparingTo("10.00");
        }
    }

    @Nested
    class UnknownAccounts {

        @Test
        void cannot_be_queried_or_transacted_against() {
            assertThatThrownBy(() -> bank.balanceOf("nope")).isInstanceOf(AccountNotFoundException.class);
            assertThatThrownBy(() -> bank.historyOf("nope")).isInstanceOf(AccountNotFoundException.class);
            assertThatThrownBy(() -> bank.deposit("nope", Amount.of("1.00"))).isInstanceOf(AccountNotFoundException.class);
            assertThatThrownBy(() -> bank.withdraw("nope", Amount.of("1.00"))).isInstanceOf(AccountNotFoundException.class);
        }
    }

    @Nested
    class UnderConcurrentLoad {

        @Test
        void concurrent_withdrawals_never_overdraw_an_account() throws Exception {
            String id = accountWithBalance("50.00");

            List<Boolean> outcomes = runConcurrently(100, () -> {
                try {
                    bank.withdraw(id, Amount.of("1.00"));
                    return true;
                } catch (InsufficientFundsException rejected) {
                    return false;
                }
            });

            assertThat(outcomes).filteredOn(succeeded -> succeeded).hasSize(50);
            assertThat(bank.balanceOf(id)).isEqualByComparingTo("0");
        }

        @Test
        @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
        void opposing_transfers_between_the_same_accounts_do_not_deadlock() throws Exception {
            String alice = accountWithBalance("1000.00");
            String bob = accountWithBalance("1000.00");

            runConcurrently(2_000, () -> ThreadLocalRandom.current().nextBoolean()
                    ? bank.transfer(alice, bob, Amount.of("1.00"))
                    : bank.transfer(bob, alice, Amount.of("1.00")));

            assertThat(bank.balanceOf(alice).add(bank.balanceOf(bob))).isEqualByComparingTo("2000.00");
        }

        @Test
        void random_transfers_conserve_the_total_money_in_the_bank() throws Exception {
            List<String> accounts = IntStream.range(0, 5).mapToObj(i -> accountWithBalance("100.00")).toList();

            runConcurrently(1_000, () -> {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                String from = accounts.get(random.nextInt(accounts.size()));
                String to = accounts.get(random.nextInt(accounts.size()));
                try {
                    return bank.transfer(from, to, Amount.of("7.00"));
                } catch (InsufficientFundsException | InvalidTransferException expected) {
                    return null;
                }
            });

            BigDecimal total = accounts.stream().map(bank::balanceOf).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(total).isEqualByComparingTo("500.00");
            assertThat(accounts).allSatisfy(id -> assertThat(bank.balanceOf(id)).isNotNegative());
        }
    }

    private String accountWithBalance(String balance) {
        String id = bank.openAccount().id();
        bank.deposit(id, Amount.of(balance));
        return id;
    }

    /**
     * Not try-with-resources: {@code ExecutorService.close()} waits for every task, so a
     * deadlock would hang the build instead of failing the test. Daemon threads let the
     * JVM exit even if some workers are stuck on a monitor.
     */
    private static <T> List<T> runConcurrently(int tasks, Callable<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(16, Thread.ofPlatform().daemon().factory());
        try {
            List<Future<T>> futures = executor.invokeAll(IntStream.range(0, tasks).mapToObj(i -> task).toList());
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
