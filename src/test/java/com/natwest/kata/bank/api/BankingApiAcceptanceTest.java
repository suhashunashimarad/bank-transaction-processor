package com.natwest.kata.bank.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BankingApiAcceptanceTest {

    private static final RequestPostProcessor ALICE = customer("alice");
    private static final RequestPostProcessor BOB = customer("bob");

    @Autowired
    private MockMvc mvc;

    @Nested
    class OpeningAnAccount {

        @Test
        void returns_the_new_account_with_a_zero_balance() throws Exception {
            mvc.perform(post("/accounts").with(ALICE))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", containsString("/accounts/")))
                    .andExpect(jsonPath("$.accountId", notNullValue()))
                    .andExpect(jsonPath("$.balance").value(0.00));
        }
    }

    @Nested
    class QueryingAnAccount {

        @Test
        void returns_its_current_balance() throws Exception {
            String id = openAccountWith("125.50");

            mvc.perform(get("/accounts/{id}", id).with(ALICE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountId").value(id))
                    .andExpect(jsonPath("$.balance").value(125.50));
        }

        @Test
        void returns_its_transaction_history_oldest_first() throws Exception {
            String id = openAccountWith("100.00");
            moneyRequest(id, "withdrawals", "40.00");

            mvc.perform(get("/accounts/{id}/transactions", id).with(ALICE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].type").value("DEPOSIT"))
                    .andExpect(jsonPath("$[0].amount").value(100.00))
                    .andExpect(jsonPath("$[1].type").value("WITHDRAWAL"))
                    .andExpect(jsonPath("$[1].amount").value(40.00))
                    .andExpect(jsonPath("$[1].balanceAfter").value(60.00))
                    .andExpect(jsonPath("$[1].timestamp", notNullValue()))
                    .andExpect(jsonPath("$[1].transactionId", notNullValue()));
        }

        @Test
        void that_does_not_exist_is_not_found() throws Exception {
            mvc.perform(get("/accounts/{id}", "unknown").with(ALICE))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Account unknown does not exist"));

            mvc.perform(get("/accounts/{id}/transactions", "unknown").with(ALICE))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class DepositingAndWithdrawing {

        @Test
        void a_deposit_returns_the_recorded_transaction() throws Exception {
            String id = openAccount();

            moneyRequest(id, "deposits", "20.00")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.type").value("DEPOSIT"))
                    .andExpect(jsonPath("$.balanceAfter").value(20.00));
        }

        @Test
        void a_withdrawal_that_would_overdraw_is_unprocessable() throws Exception {
            String id = openAccountWith("10.00");

            moneyRequest(id, "withdrawals", "10.01")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.title").value("Insufficient funds"))
                    .andExpect(jsonPath("$.detail", containsString("available 10.00")));
        }

        @Test
        void a_non_positive_amount_is_a_bad_request() throws Exception {
            String id = openAccount();

            moneyRequest(id, "deposits", "0")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid amount"));
            moneyRequest(id, "withdrawals", "-5")
                    .andExpect(status().isBadRequest());
        }

        @Test
        void an_amount_with_fractions_of_a_penny_is_a_bad_request() throws Exception {
            moneyRequest(openAccount(), "deposits", "1.001")
                    .andExpect(status().isBadRequest());
        }

        @Test
        void a_missing_amount_is_a_bad_request() throws Exception {
            mvc.perform(post("/accounts/{id}/deposits", openAccount())
                            .with(ALICE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void a_malformed_body_is_a_bad_request() throws Exception {
            mvc.perform(post("/accounts/{id}/deposits", openAccount())
                            .with(ALICE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\": \"lots\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void against_an_unknown_account_is_not_found() throws Exception {
            moneyRequest("unknown", "deposits", "1.00")
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Transferring {

        @Test
        void moves_money_and_returns_the_transfer() throws Exception {
            String alice = openAccountWith("100.00");
            String bob = openAccount();

            transfer(alice, bob, "30.00")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.transactionId", notNullValue()))
                    .andExpect(jsonPath("$.fromAccountId").value(alice))
                    .andExpect(jsonPath("$.toAccountId").value(bob))
                    .andExpect(jsonPath("$.amount").value(30.00));

            mvc.perform(get("/accounts/{id}", alice).with(ALICE)).andExpect(jsonPath("$.balance").value(70.00));
            mvc.perform(get("/accounts/{id}", bob).with(ALICE)).andExpect(jsonPath("$.balance").value(30.00));
            mvc.perform(get("/accounts/{id}/transactions", bob).with(ALICE))
                    .andExpect(jsonPath("$[0].type").value("TRANSFER_IN"))
                    .andExpect(jsonPath("$[0].counterpartyAccountId").value(alice));
        }

        @Test
        void that_would_overdraw_the_sender_is_unprocessable() throws Exception {
            transfer(openAccountWith("5.00"), openAccount(), "5.01")
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void to_the_same_account_is_a_bad_request() throws Exception {
            String alice = openAccountWith("5.00");

            transfer(alice, alice, "1.00")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid transfer"));
        }

        @Test
        void involving_an_unknown_account_is_not_found() throws Exception {
            transfer(openAccountWith("5.00"), "unknown", "1.00")
                    .andExpect(status().isNotFound());
        }

        @Test
        void without_both_accounts_is_a_bad_request() throws Exception {
            mvc.perform(post("/transfers")
                            .with(ALICE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fromAccountId\": \"a\", \"amount\": 1.00}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail", containsString("toAccountId")));
        }
    }

    @Nested
    class Security {

        @Test
        void a_request_without_a_bearer_token_is_unauthorized() throws Exception {
            mvc.perform(post("/accounts")).andExpect(status().isUnauthorized());
            mvc.perform(get("/accounts/{id}", openAccount())).andExpect(status().isUnauthorized());
            mvc.perform(post("/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void a_customer_cannot_see_someone_elses_account() throws Exception {
            String alices = openAccountWith("10.00");

            mvc.perform(get("/accounts/{id}", alices).with(BOB))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.title").value("Access denied"));
            mvc.perform(get("/accounts/{id}/transactions", alices).with(BOB))
                    .andExpect(status().isForbidden());
        }

        @Test
        void a_customer_cannot_deposit_into_or_withdraw_from_someone_elses_account() throws Exception {
            String alices = openAccountWith("10.00");

            moneyRequest(BOB, alices, "deposits", "1.00").andExpect(status().isForbidden());
            moneyRequest(BOB, alices, "withdrawals", "1.00").andExpect(status().isForbidden());

            mvc.perform(get("/accounts/{id}", alices).with(ALICE)).andExpect(jsonPath("$.balance").value(10.00));
        }

        @Test
        void a_customer_cannot_transfer_out_of_someone_elses_account() throws Exception {
            String alices = openAccountWith("10.00");
            String bobs = openAccount(BOB);

            transfer(BOB, alices, bobs, "1.00").andExpect(status().isForbidden());

            mvc.perform(get("/accounts/{id}", alices).with(ALICE)).andExpect(jsonPath("$.balance").value(10.00));
        }

        @Test
        void a_customer_can_transfer_into_someone_elses_account() throws Exception {
            String bobs = openAccount(BOB);

            transfer(openAccountWith("10.00"), bobs, "4.00").andExpect(status().isCreated());

            mvc.perform(get("/accounts/{id}", bobs).with(BOB)).andExpect(jsonPath("$.balance").value(4.00));
        }
    }

    private static RequestPostProcessor customer(String subject) {
        return jwt().jwt(token -> token.subject(subject));
    }

    private String openAccount() throws Exception {
        return openAccount(ALICE);
    }

    private String openAccount(RequestPostProcessor customer) throws Exception {
        String body = mvc.perform(post("/accounts").with(customer)).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accountId");
    }

    private String openAccountWith(String balance) throws Exception {
        String id = openAccount();
        moneyRequest(id, "deposits", balance).andExpect(status().isCreated());
        return id;
    }

    private ResultActions moneyRequest(String accountId, String operation, String amount) throws Exception {
        return moneyRequest(ALICE, accountId, operation, amount);
    }

    private ResultActions moneyRequest(RequestPostProcessor customer, String accountId, String operation, String amount)
            throws Exception {
        return mvc.perform(post("/accounts/{id}/{operation}", accountId, operation)
                .with(customer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": %s}".formatted(amount)));
    }

    private ResultActions transfer(String from, String to, String amount) throws Exception {
        return transfer(ALICE, from, to, amount);
    }

    private ResultActions transfer(RequestPostProcessor customer, String from, String to, String amount) throws Exception {
        return mvc.perform(post("/transfers")
                .with(customer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fromAccountId\": \"%s\", \"toAccountId\": \"%s\", \"amount\": %s}".formatted(from, to, amount)));
    }
}
