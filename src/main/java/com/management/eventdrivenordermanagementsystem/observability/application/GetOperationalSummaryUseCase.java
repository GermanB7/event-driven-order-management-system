package com.management.eventdrivenordermanagementsystem.observability.application;

import com.management.eventdrivenordermanagementsystem.observability.application.dto.OperationalSummaryView;
import com.management.eventdrivenordermanagementsystem.observability.application.port.OperationalInspectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetOperationalSummaryUseCase {

    private final OperationalInspectionRepository operationalInspectionRepository;

    public GetOperationalSummaryUseCase(OperationalInspectionRepository operationalInspectionRepository) {
        this.operationalInspectionRepository = operationalInspectionRepository;
    }

    @Transactional(readOnly = true)
    public OperationalSummaryView execute() {
        return operationalInspectionRepository.findOperationalSummary();
    }
}

