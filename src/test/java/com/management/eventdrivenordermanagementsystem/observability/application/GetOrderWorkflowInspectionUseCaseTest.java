package com.management.eventdrivenordermanagementsystem.observability.application;

import com.management.eventdrivenordermanagementsystem.observability.application.dto.OrderWorkflowInspectionView;
import com.management.eventdrivenordermanagementsystem.observability.application.dto.WorkflowTimelineEventView;
import com.management.eventdrivenordermanagementsystem.observability.application.port.WorkflowTimelineRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetOrderWorkflowInspectionUseCaseTest {

    @Mock
    private WorkflowTimelineRepository workflowTimelineRepository;

    @Test
    void executesInspectionSortingTimelineAndDerivingCurrentState() {
        UUID orderId = UUID.randomUUID();

        WorkflowTimelineEventView paymentRequested = new WorkflowTimelineEventView(
            UUID.randomUUID(),
            "PAYMENT_AUTHORIZATION_REQUESTED",
            "wf-901",
            "corr-901",
            "cause-1",
            "PENDING",
            0,
            Instant.parse("2026-04-01T10:00:02Z"),
            null,
            null
        );

        WorkflowTimelineEventView orderCreated = new WorkflowTimelineEventView(
            UUID.randomUUID(),
            "ORDER_CREATED",
            "wf-901",
            "corr-901",
            null,
            "PUBLISHED",
            0,
            Instant.parse("2026-04-01T10:00:00Z"),
            Instant.parse("2026-04-01T10:00:01Z"),
            null
        );

        when(workflowTimelineRepository.findByOrderId(orderId)).thenReturn(List.of(paymentRequested, orderCreated));

        GetOrderWorkflowInspectionUseCase useCase = new GetOrderWorkflowInspectionUseCase(workflowTimelineRepository);

        OrderWorkflowInspectionView view = useCase.execute(orderId);

        assertThat(view.orderId()).isEqualTo(orderId);
        assertThat(view.workflowId()).isEqualTo("wf-901");
        assertThat(view.currentWorkflowState()).isEqualTo("PAYMENT_AUTHORIZATION_REQUESTED");
        assertThat(view.timeline()).extracting(WorkflowTimelineEventView::eventType)
            .containsExactly("ORDER_CREATED", "PAYMENT_AUTHORIZATION_REQUESTED");
    }

    @Test
    void executesInspectionWithNoTimelineEvents() {
        UUID orderId = UUID.randomUUID();
        when(workflowTimelineRepository.findByOrderId(orderId)).thenReturn(List.of());

        GetOrderWorkflowInspectionUseCase useCase = new GetOrderWorkflowInspectionUseCase(workflowTimelineRepository);

        OrderWorkflowInspectionView view = useCase.execute(orderId);

        assertThat(view.orderId()).isEqualTo(orderId);
        assertThat(view.workflowId()).isNull();
        assertThat(view.currentWorkflowState()).isEqualTo("NO_EVENTS");
        assertThat(view.timeline()).isEmpty();
    }
}

