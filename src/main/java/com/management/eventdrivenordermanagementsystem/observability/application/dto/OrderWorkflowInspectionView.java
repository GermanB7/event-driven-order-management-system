package com.management.eventdrivenordermanagementsystem.observability.application.dto;

import java.util.List;
import java.util.UUID;

public record OrderWorkflowInspectionView(
    UUID orderId,
    String workflowId,
    String currentWorkflowState,
    List<WorkflowTimelineEventView> timeline
) {
}

