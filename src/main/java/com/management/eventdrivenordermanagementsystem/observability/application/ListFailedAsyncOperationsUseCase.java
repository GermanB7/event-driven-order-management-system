package com.management.eventdrivenordermanagementsystem.observability.application;

import com.management.eventdrivenordermanagementsystem.observability.application.dto.FailedAsyncOperationView;
import com.management.eventdrivenordermanagementsystem.observability.application.port.OperationalInspectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListFailedAsyncOperationsUseCase {

    private final OperationalInspectionRepository operationalInspectionRepository;

    public ListFailedAsyncOperationsUseCase(OperationalInspectionRepository operationalInspectionRepository) {
        this.operationalInspectionRepository = operationalInspectionRepository;
    }

    @Transactional(readOnly = true)
    public List<FailedAsyncOperationView> execute() {
        return operationalInspectionRepository.findFailedOperations();
    }
}

