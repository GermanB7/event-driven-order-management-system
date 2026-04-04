package com.management.eventdrivenordermanagementsystem.observability.application;

import com.management.eventdrivenordermanagementsystem.observability.application.dto.WorkflowStatusAggregateView;
import com.management.eventdrivenordermanagementsystem.observability.application.port.OperationalInspectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetWorkflowStatusAggregateUseCase {

    private final OperationalInspectionRepository operationalInspectionRepository;

    public GetWorkflowStatusAggregateUseCase(OperationalInspectionRepository operationalInspectionRepository) {
        this.operationalInspectionRepository = operationalInspectionRepository;
    }

    @Transactional(readOnly = true)
    public WorkflowStatusAggregateView execute(UUID orderId) {
        return operationalInspectionRepository.findWorkflowStatusAggregate(orderId);
    }
}

