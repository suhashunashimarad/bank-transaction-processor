package com.natwest.kata.bank.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmountTest {

    @Test
    void is_normalised_to_two_decimal_places() {
        assertThat(Amount.of("10").value()).isEqualTo(new BigDecimal("10.00"));
        assertThat(Amount.of("10.5").value()).isEqualTo(new BigDecimal("10.50"));
    }

    @Test
    void amounts_with_the_same_value_are_equal_regardless_of_scale() {
        assertThat(Amount.of("10")).isEqualTo(Amount.of("10.00"));
    }

    @Test
    void the_smallest_amount_is_one_penny() {
        assertThat(Amount.of("0.01").value()).isEqualTo(new BigDecimal("0.01"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-0.01", "-100"})
    void must_be_positive(String value) {
        assertThatThrownBy(() -> Amount.of(value))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("greater than zero");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.001", "10.999"})
    void cannot_contain_fractions_of_a_penny(String value) {
        assertThatThrownBy(() -> Amount.of(value))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("2 decimal places");
    }

    @Test
    void is_required() {
        assertThatThrownBy(() -> new Amount(null))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("required");
    }
}
