package com.management.eventdrivenordermanagementsystem.observability.application.port;

import com.management.eventdrivenordermanagementsystem.observability.application.dto.WorkflowTimelineEventView;

import java.util.List;
import java.util.UUID;

public interface WorkflowTimelineRepository {

    List<WorkflowTimelineEventView> findByOrderId(UUID orderId);
}

