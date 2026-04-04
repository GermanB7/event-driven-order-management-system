package com.management.eventdrivenordermanagementsystem.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(
    packages = "com.management.eventdrivenordermanagementsystem",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class ModuleBoundariesArchTest {

    @ArchTest
    static final ArchRule modular_boundaries_are_respected = layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        .layer("Orders").definedBy("com.management.eventdrivenordermanagementsystem.orders..")
        .layer("Inventory").definedBy("com.management.eventdrivenordermanagementsystem.inventory..")
        .layer("Payments").definedBy("com.management.eventdrivenordermanagementsystem.payments..")
        .layer("Shipping").definedBy("com.management.eventdrivenordermanagementsystem.shipping..")
        .layer("Workflow").definedBy("com.management.eventdrivenordermanagementsystem.workflow..")
        .layer("Messaging").definedBy("com.management.eventdrivenordermanagementsystem.messaging..")
        .layer("Outbox").definedBy("com.management.eventdrivenordermanagementsystem.outbox..")
        .layer("Observability").definedBy("com.management.eventdrivenordermanagementsystem.observability..")
        .layer("Shared").definedBy("com.management.eventdrivenordermanagementsystem.shared..")

        .whereLayer("Orders").mayOnlyAccessLayers("Orders", "Workflow", "Messaging", "Shared")
        .whereLayer("Inventory").mayOnlyAccessLayers("Inventory", "Workflow", "Messaging", "Shared")
        .whereLayer("Payments").mayOnlyAccessLayers("Payments", "Workflow", "Messaging", "Shared")
        .whereLayer("Shipping").mayOnlyAccessLayers("Shipping", "Workflow", "Messaging", "Shared")
        .whereLayer("Workflow").mayOnlyAccessLayers("Workflow", "Messaging", "Outbox", "Observability", "Shared")
        .whereLayer("Messaging").mayOnlyAccessLayers("Messaging", "Outbox", "Observability", "Shared")
        .whereLayer("Outbox").mayOnlyAccessLayers("Outbox", "Messaging", "Observability", "Shared")
        .whereLayer("Observability").mayOnlyAccessLayers("Observability", "Shared")
        .whereLayer("Shared").mayNotAccessAnyLayer();
}

