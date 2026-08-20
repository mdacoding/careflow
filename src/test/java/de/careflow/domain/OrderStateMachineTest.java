package de.careflow.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    @Test
    void labHappyPath() {
        assertThat(OrderStateMachine.allowed(OrderKind.LAB, OrderStatus.PLACED))
                .contains(OrderStatus.IN_LAB, OrderStatus.CANCELLED);
        OrderStateMachine.require(OrderKind.LAB, OrderStatus.IN_LAB, OrderStatus.RESULTED);
    }

    @Test
    void rejectsIllegalMedicationTransition() {
        assertThatThrownBy(() -> OrderStateMachine.require(OrderKind.MEDICATION, OrderStatus.ACTIVE, OrderStatus.RESULTED))
                .isInstanceOf(IllegalOrderStateException.class);
    }
}
