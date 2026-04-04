package com.management.eventdrivenordermanagementsystem.observability.application;

import com.management.eventdrivenordermanagementsystem.observability.application.dto.WorkflowTimelineEventView;
import com.management.eventdrivenordermanagementsystem.observability.application.port.OperationalInspectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FindEventsByEventIdUseCase {

    private final OperationalInspectionRepository operationalInspectionRepository;

    public FindEventsByEventIdUseCase(OperationalInspectionRepository operationalInspectionRepository) {
        this.operationalInspectionRepository = operationalInspectionRepository;
    }

    @Transactional(readOnly = true)
    public List<WorkflowTimelineEventView> execute(UUID eventId) {
        return operationalInspectionRepository.findEventsByEventId(eventId);
    }
}

