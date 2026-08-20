package de.careflow.lab;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceRangeInterpreterTest {

    @Test
    void leukocytesAboveRangeAreHighNotPanic() {
        assertThat(ReferenceRangeInterpreter.interpret(
                "6690-2", new BigDecimal("14.8"), new BigDecimal("4.0"), new BigDecimal("10.0")))
                .isEqualTo("H");
    }

    @Test
    void crpAbovePanicIsHh() {
        assertThat(ReferenceRangeInterpreter.interpret(
                "1988-5", new BigDecimal("86"), new BigDecimal("0"), new BigDecimal("5")))
                .isEqualTo("HH");
    }

    @Test
    void normalHemoglobin() {
        assertThat(ReferenceRangeInterpreter.interpret(
                "718-7", new BigDecimal("14.0"), new BigDecimal("12.0"), new BigDecimal("16.0")))
                .isEqualTo("N");
    }
}
