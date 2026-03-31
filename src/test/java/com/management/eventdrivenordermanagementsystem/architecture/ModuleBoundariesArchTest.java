package com.management.eventdrivenordermanagementsystem.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "com.management.eventdrivenordermanagementsystem")
class ModuleBoundariesArchTest {

    @ArchTest
    static final ArchRule modular_boundaries_are_respected = layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        .layer("Orders").definedBy("..orders..")
        .layer("Inventory").definedBy("..inventory..")
        .layer("Payments").definedBy("..payments..")
        .layer("Shipping").definedBy("..shipping..")
        .layer("Workflow").definedBy("..workflow..")
        .layer("Messaging").definedBy("..messaging..")
        .layer("Outbox").definedBy("..outbox..")
        .layer("Observability").definedBy("..observability..")
        .layer("Shared").definedBy("..shared..")

        .whereLayer("Orders").mayOnlyAccessLayers("Workflow", "Messaging", "Shared")
        .whereLayer("Inventory").mayOnlyAccessLayers("Workflow", "Messaging", "Shared")
        .whereLayer("Payments").mayOnlyAccessLayers("Workflow", "Messaging", "Shared")
        .whereLayer("Shipping").mayOnlyAccessLayers("Workflow", "Messaging", "Shared")
        .whereLayer("Workflow").mayOnlyAccessLayers("Messaging", "Outbox", "Observability", "Shared")
        .whereLayer("Messaging").mayOnlyAccessLayers("Outbox", "Observability", "Shared")
        .whereLayer("Outbox").mayOnlyAccessLayers("Messaging", "Observability", "Shared")
        .whereLayer("Observability").mayOnlyAccessLayers("Shared")
        .whereLayer("Shared").mayNotAccessAnyLayer();
}

