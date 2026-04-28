package com.management.eventdrivenordermanagementsystem.inventory.infrastructure.persistence;

import com.management.eventdrivenordermanagementsystem.inventory.application.port.InventoryRepository;
import com.management.eventdrivenordermanagementsystem.inventory.domain.InventoryItem;
import com.management.eventdrivenordermanagementsystem.inventory.domain.InventoryReservation;
import com.management.eventdrivenordermanagementsystem.inventory.domain.InventoryReservationStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcInventoryRepository implements InventoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcInventoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<InventoryItem> findItemBySku(String sku) {
        return jdbcTemplate.query(
            """
                select sku, available_quantity, reserved_quantity, updated_at
                from inventory.inventory_items
                where sku = ?
                """,
            (ResultSet rs, int rowNum) -> mapInventoryItem(rs),
            sku
        ).stream().findFirst();
    }

    @Override
    public InventoryItem saveItem(InventoryItem item) {
        jdbcTemplate.update(
            """
                update inventory.inventory_items
                set available_quantity = ?,
                    reserved_quantity = ?,
                    updated_at = ?
                where sku = ?
                """,
            item.availableQuantity(),
            item.reservedQuantity(),
            Timestamp.from(item.updatedAt()),
            item.sku()
        );
        return item;
    }

    @Override
    public InventoryReservation saveReservation(InventoryReservation reservation) {
        jdbcTemplate.update(
            """
                insert into inventory.inventory_reservations (id, order_id, sku, quantity, status, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
            reservation.id(),
            reservation.orderId(),
            reservation.sku(),
            reservation.quantity(),
            reservation.status().name(),
            Timestamp.from(reservation.createdAt()),
            Timestamp.from(reservation.updatedAt())
        );
        return reservation;
    }

    @Override
    public List<InventoryReservation> findReservationsByOrderId(UUID orderId) {
        return jdbcTemplate.query(
            """
                select id, order_id, sku, quantity, status, created_at, updated_at
                from inventory.inventory_reservations
                where order_id = ?
                """,
            (ResultSet rs, int rowNum) -> mapInventoryReservation(rs),
            orderId
        );
    }

    @Override
    public InventoryReservation updateReservationStatus(UUID reservationId, InventoryReservationStatus status, Instant updatedAt) {
        jdbcTemplate.update(
            """
                update inventory.inventory_reservations
                set status = ?,
                    updated_at = ?
                where id = ?
                """,
            status.name(),
            Timestamp.from(updatedAt),
            reservationId
        );

        return jdbcTemplate.queryForObject(
            """
                select id, order_id, sku, quantity, status, created_at, updated_at
                from inventory.inventory_reservations
                where id = ?
                """,
            (ResultSet rs, int rowNum) -> mapInventoryReservation(rs),
            reservationId
        );
    }

    @Override
    public boolean markMessageProcessed(String consumerName, UUID messageId, Instant processedAt) {
        try {
            jdbcTemplate.update(
                """
                    insert into inventory.processed_messages (consumer_name, message_id, processed_at)
                    values (?, ?, ?)
                    """,
                consumerName,
                messageId,
                Timestamp.from(processedAt)
            );
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    private InventoryItem mapInventoryItem(ResultSet rs) throws SQLException {
        return new InventoryItem(
            rs.getString("sku"),
            rs.getInt("available_quantity"),
            rs.getInt("reserved_quantity"),
            toInstant(rs.getTimestamp("updated_at"))
        );
    }

    private InventoryReservation mapInventoryReservation(ResultSet rs) throws SQLException {
        return new InventoryReservation(
            rs.getObject("id", UUID.class),
            rs.getObject("order_id", UUID.class),
            rs.getString("sku"),
            rs.getInt("quantity"),
            InventoryReservationStatus.valueOf(rs.getString("status")),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at"))
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

