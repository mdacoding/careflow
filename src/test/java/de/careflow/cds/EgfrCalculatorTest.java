package de.careflow.cds;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EgfrCalculatorTest {

    @Test
    void ckdEpi2021MaleReducedKidneyFunction() {
        Double egfr = EgfrCalculator.ckdEpi2021(1.7, 79, "M");
        assertThat(egfr).isNotNull().isCloseTo(40.0, within(8.0));
        assertThat(egfr).isGreaterThan(30.0).isLessThan(60.0);
    }

    @Test
    void returnsNullWhenCreatinineMissing() {
        assertThat(EgfrCalculator.ckdEpi2021(null, 70, "F")).isNull();
    }
}
