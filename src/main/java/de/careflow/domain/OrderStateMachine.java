package de.careflow.domain;

import java.util.EnumSet;
import java.util.Set;

public final class OrderStateMachine {

    private OrderStateMachine() {
    }

    public static Set<OrderStatus> allowed(OrderKind kind, OrderStatus from) {
        if (kind == OrderKind.LAB) {
            return switch (from) {
                case PLACED -> EnumSet.of(OrderStatus.IN_LAB, OrderStatus.CANCELLED);
                case IN_LAB -> EnumSet.of(OrderStatus.RESULTED, OrderStatus.CANCELLED);
                case RESULTED, ACTIVE, BLOCKED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
            };
        }
        return switch (from) {
            case ACTIVE -> EnumSet.of(OrderStatus.CANCELLED);
            case BLOCKED, PLACED, IN_LAB, RESULTED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public static void require(OrderKind kind, OrderStatus from, OrderStatus to) {
        if (!allowed(kind, from).contains(to)) {
            throw new IllegalOrderStateException(
                    "Übergang %s → %s ist für %s nicht erlaubt".formatted(from, to, kind));
        }
    }
}
