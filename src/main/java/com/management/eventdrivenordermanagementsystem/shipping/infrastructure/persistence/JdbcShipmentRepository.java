package com.management.eventdrivenordermanagementsystem.shipping.infrastructure.persistence;

import com.management.eventdrivenordermanagementsystem.shipping.application.port.ShipmentRepository;
import com.management.eventdrivenordermanagementsystem.shipping.domain.Shipment;
import com.management.eventdrivenordermanagementsystem.shipping.domain.ShipmentStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcShipmentRepository implements ShipmentRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcShipmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Shipment save(Shipment shipment) {
        jdbcTemplate.update(
            """
                insert into shipping.shipments (id, order_id, status, created_at, updated_at)
                values (?, ?, ?, ?, ?)
                """,
            shipment.id(),
            shipment.orderId(),
            shipment.status().name(),
            Timestamp.from(shipment.createdAt()),
            Timestamp.from(shipment.updatedAt())
        );
        return shipment;
    }

    @Override
    public Optional<Shipment> findByOrderId(UUID orderId) {
        return jdbcTemplate.query(
            """
                select id, order_id, status, created_at, updated_at
                from shipping.shipments
                where order_id = ?
                """,
            (ResultSet rs, int rowNum) -> mapShipment(rs),
            orderId
        ).stream().findFirst();
    }

    private Shipment mapShipment(ResultSet rs) throws SQLException {
        return new Shipment(
            rs.getObject("id", UUID.class),
            rs.getObject("order_id", UUID.class),
            ShipmentStatus.valueOf(rs.getString("status")),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at"))
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}


