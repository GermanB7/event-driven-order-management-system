package com.management.eventdrivenordermanagementsystem.observability.application;

import com.management.eventdrivenordermanagementsystem.observability.application.dto.WorkflowTimelineEventView;
import com.management.eventdrivenordermanagementsystem.observability.application.port.OperationalInspectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FindEventsByWorkflowIdUseCase {

    private final OperationalInspectionRepository operationalInspectionRepository;

    public FindEventsByWorkflowIdUseCase(OperationalInspectionRepository operationalInspectionRepository) {
        this.operationalInspectionRepository = operationalInspectionRepository;
    }

    @Transactional(readOnly = true)
    public List<WorkflowTimelineEventView> execute(String workflowId) {
        return operationalInspectionRepository.findEventsByWorkflowId(workflowId);
    }
}

