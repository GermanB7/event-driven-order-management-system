package com.management.eventdrivenordermanagementsystem.observability.application;

import com.management.eventdrivenordermanagementsystem.observability.application.dto.OrderWorkflowInspectionView;
import com.management.eventdrivenordermanagementsystem.observability.application.dto.WorkflowTimelineEventView;
import com.management.eventdrivenordermanagementsystem.observability.application.port.WorkflowTimelineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class GetOrderWorkflowInspectionUseCase {

    private final WorkflowTimelineRepository workflowTimelineRepository;

    public GetOrderWorkflowInspectionUseCase(WorkflowTimelineRepository workflowTimelineRepository) {
        this.workflowTimelineRepository = workflowTimelineRepository;
    }

    @Transactional(readOnly = true)
    public OrderWorkflowInspectionView execute(UUID orderId) {
        List<WorkflowTimelineEventView> timeline = workflowTimelineRepository.findByOrderId(orderId)
            .stream()
            .sorted(Comparator.comparing(WorkflowTimelineEventView::occurredAt).thenComparing(WorkflowTimelineEventView::eventId))
            .toList();

        String workflowId = timeline.stream()
            .map(WorkflowTimelineEventView::workflowId)
            .filter(id -> id != null && !id.isBlank())
            .findFirst()
            .orElse(null);

        String currentWorkflowState = timeline.isEmpty()
            ? "NO_EVENTS"
            : timeline.get(timeline.size() - 1).eventType();

        return new OrderWorkflowInspectionView(orderId, workflowId, currentWorkflowState, timeline);
    }
}

