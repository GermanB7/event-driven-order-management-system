package com.management.eventdrivenordermanagementsystem.observability.application.port;

import com.management.eventdrivenordermanagementsystem.observability.application.dto.FailedAsyncOperationView;
import com.management.eventdrivenordermanagementsystem.observability.application.dto.WorkflowStatusAggregateView;
import com.management.eventdrivenordermanagementsystem.observability.application.dto.OperationalSummaryView;
import com.management.eventdrivenordermanagementsystem.observability.application.dto.WorkflowTimelineEventView;

import java.util.List;
import java.util.UUID;

public interface OperationalInspectionRepository {

    List<WorkflowTimelineEventView> findEventsByWorkflowId(String workflowId);

    List<WorkflowTimelineEventView> findEventsByEventId(UUID eventId);

    List<FailedAsyncOperationView> findFailedOperations();

    List<FailedAsyncOperationView> findFailedOperationsByOrderId(UUID orderId);

    WorkflowStatusAggregateView findWorkflowStatusAggregate(UUID orderId);

    OperationalSummaryView findOperationalSummary();
}

