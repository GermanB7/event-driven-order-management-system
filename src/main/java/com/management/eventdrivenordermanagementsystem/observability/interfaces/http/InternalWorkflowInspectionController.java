package com.management.eventdrivenordermanagementsystem.observability.interfaces.http;

import com.management.eventdrivenordermanagementsystem.observability.application.FindEventsByEventIdUseCase;
import com.management.eventdrivenordermanagementsystem.observability.application.FindEventsByWorkflowIdUseCase;
import com.management.eventdrivenordermanagementsystem.observability.application.GetOperationalSummaryUseCase;
import com.management.eventdrivenordermanagementsystem.observability.application.GetOrderWorkflowInspectionUseCase;
import com.management.eventdrivenordermanagementsystem.observability.application.GetWorkflowStatusAggregateUseCase;
import com.management.eventdrivenordermanagementsystem.observability.application.ListFailedAsyncOperationsUseCase;
import com.management.eventdrivenordermanagementsystem.observability.application.dto.FailedAsyncOperationView;
import com.management.eventdrivenordermanagementsystem.observability.application.dto.OperationalSummaryView;
import com.management.eventdrivenordermanagementsystem.observability.application.dto.OrderWorkflowInspectionView;
import com.management.eventdrivenordermanagementsystem.observability.application.dto.WorkflowStatusAggregateView;
import com.management.eventdrivenordermanagementsystem.observability.application.dto.WorkflowTimelineEventView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal")
public class InternalWorkflowInspectionController {

    private final GetOrderWorkflowInspectionUseCase getOrderWorkflowInspectionUseCase;
    private final FindEventsByWorkflowIdUseCase findEventsByWorkflowIdUseCase;
    private final FindEventsByEventIdUseCase findEventsByEventIdUseCase;
    private final ListFailedAsyncOperationsUseCase listFailedAsyncOperationsUseCase;
    private final GetWorkflowStatusAggregateUseCase getWorkflowStatusAggregateUseCase;
    private final GetOperationalSummaryUseCase getOperationalSummaryUseCase;

    public InternalWorkflowInspectionController(
        GetOrderWorkflowInspectionUseCase getOrderWorkflowInspectionUseCase,
        FindEventsByWorkflowIdUseCase findEventsByWorkflowIdUseCase,
        FindEventsByEventIdUseCase findEventsByEventIdUseCase,
        ListFailedAsyncOperationsUseCase listFailedAsyncOperationsUseCase,
        GetWorkflowStatusAggregateUseCase getWorkflowStatusAggregateUseCase,
        GetOperationalSummaryUseCase getOperationalSummaryUseCase
    ) {
        this.getOrderWorkflowInspectionUseCase = getOrderWorkflowInspectionUseCase;
        this.findEventsByWorkflowIdUseCase = findEventsByWorkflowIdUseCase;
        this.findEventsByEventIdUseCase = findEventsByEventIdUseCase;
        this.listFailedAsyncOperationsUseCase = listFailedAsyncOperationsUseCase;
        this.getWorkflowStatusAggregateUseCase = getWorkflowStatusAggregateUseCase;
        this.getOperationalSummaryUseCase = getOperationalSummaryUseCase;
    }

    @GetMapping("/orders/{orderId}/workflow")
    public OrderWorkflowInspectionView inspectOrderWorkflow(@PathVariable UUID orderId) {
        return getOrderWorkflowInspectionUseCase.execute(orderId);
    }

    @GetMapping("/orders/{orderId}/status")
    public WorkflowStatusAggregateView inspectOrderStatus(@PathVariable UUID orderId) {
        return getWorkflowStatusAggregateUseCase.execute(orderId);
    }

    @GetMapping("/workflows")
    public List<WorkflowTimelineEventView> findWorkflowEvents(@RequestParam String workflowId) {
        return findEventsByWorkflowIdUseCase.execute(workflowId);
    }

    @GetMapping("/events/{eventId}")
    public List<WorkflowTimelineEventView> findEvent(@PathVariable UUID eventId) {
        return findEventsByEventIdUseCase.execute(eventId);
    }

    @GetMapping("/failures")
    public List<FailedAsyncOperationView> listFailedOperations() {
        return listFailedAsyncOperationsUseCase.execute();
    }

    @GetMapping("/summary")
    public OperationalSummaryView getOperationalSummary() {
        return getOperationalSummaryUseCase.execute();
    }
}


