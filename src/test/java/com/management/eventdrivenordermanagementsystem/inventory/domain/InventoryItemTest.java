package com.management.eventdrivenordermanagementsystem.inventory.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryItemTest {

    @Test
    void releaseMovesReservedQuantityBackToAvailableQuantity() {
        Instant now = Instant.parse("2026-04-01T10:00:00Z");
        InventoryItem item = InventoryItem.create("SKU-1", 8, 2, now);

        InventoryItem released = item.release(2, Instant.parse("2026-04-01T10:00:05Z"));

        assertThat(released.availableQuantity()).isEqualTo(10);
        assertThat(released.reservedQuantity()).isZero();
    }

    @Test
    void releaseRejectsQuantityGreaterThanReservedQuantity() {
        InventoryItem item = InventoryItem.create("SKU-1", 8, 1, Instant.parse("2026-04-01T10:00:00Z"));

        assertThatThrownBy(() -> item.release(2, Instant.parse("2026-04-01T10:00:05Z")))
            .isInstanceOf(InventoryDomainException.class);
    }
}
