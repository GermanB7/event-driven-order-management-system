package com.management.eventdrivenordermanagementsystem.payments.infrastructure.persistence;

import com.management.eventdrivenordermanagementsystem.payments.application.port.PaymentRepository;
import com.management.eventdrivenordermanagementsystem.payments.domain.Payment;
import com.management.eventdrivenordermanagementsystem.payments.domain.PaymentStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPaymentRepository implements PaymentRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPaymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Payment save(Payment payment) {
        jdbcTemplate.update(
            """
                insert into payments.payments (id, order_id, amount, currency, status, failure_reason, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
            payment.id(),
            payment.orderId(),
            payment.amount(),
            payment.currency(),
            payment.status().name(),
            payment.failureReason(),
            Timestamp.from(payment.createdAt()),
            Timestamp.from(payment.updatedAt())
        );
        return payment;
    }

    @Override
    public Optional<Payment> findByOrderId(UUID orderId) {
        return jdbcTemplate.query(
            """
                select id, order_id, amount, currency, status, failure_reason, created_at, updated_at
                from payments.payments
                where order_id = ?
                """,
            (ResultSet rs, int rowNum) -> mapPayment(rs),
            orderId
        ).stream().findFirst();
    }

    private Payment mapPayment(ResultSet rs) throws SQLException {
        return new Payment(
            rs.getObject("id", UUID.class),
            rs.getObject("order_id", UUID.class),
            rs.getBigDecimal("amount"),
            rs.getString("currency"),
            PaymentStatus.valueOf(rs.getString("status")),
            rs.getString("failure_reason"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at"))
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

