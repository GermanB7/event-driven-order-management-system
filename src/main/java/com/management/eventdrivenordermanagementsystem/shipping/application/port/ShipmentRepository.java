package com.management.eventdrivenordermanagementsystem.shipping.application.port;

import com.management.eventdrivenordermanagementsystem.shipping.domain.Shipment;

import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository {

    Shipment save(Shipment shipment);

    Optional<Shipment> findByOrderId(UUID orderId);
}

