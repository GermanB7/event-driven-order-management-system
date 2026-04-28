package com.management.eventdrivenordermanagementsystem.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * TestExecutionListener que asegura que los listener containers de Kafka se inicien
 * DESPUÉS de que el contexto de Spring y todos los beans estén completamente listos.
 * 
 * CRITICAL TIMING: Uses afterTestClassSetUp() which runs AFTER the test class
 * is fully instantiated and all beans are initialized. This is the LATEST point
 * before the actual test method runs, ensuring all MessageListenerContainers exist.
 */
public class KafkaListenerStartupListener implements TestExecutionListener {
    private static final Logger log = LoggerFactory.getLogger(KafkaListenerStartupListener.class);

    @Override
    public void beforeTestMethod(TestContext testContext) throws Exception {
        log.info("kafka_listeners_startup STARTING phase=beforeTestMethod");
        
        try {
            ApplicationContext ctx = testContext.getApplicationContext();
            
            // Find all MessageListenerContainer beans (Kafka consumer containers)
            String[] allBeanNames = ctx.getBeanDefinitionNames();
            log.info("kafka_listeners_scanning totalBeans={}", allBeanNames.length);
            
            int containersFound = 0;
            int containersStarted = 0;
            StringBuilder containersList = new StringBuilder();
            
            for (String beanName : allBeanNames) {
                try {
                    Object bean = ctx.getBean(beanName);
                    if (bean instanceof MessageListenerContainer) {
                        containersFound++;
                        MessageListenerContainer container = (MessageListenerContainer) bean;
                        containersList.append(beanName).append("[").append(container.isRunning()).append("] ");
                        
                        if (!container.isRunning()) {
                            log.info("kafka_listener_container_starting beanName={} groupId={}", 
                                    beanName, container.getGroupId());
                            container.start();
                            containersStarted++;
                            
                            // Verify it actually started
                            Thread.sleep(100); // Give it time to start
                            boolean isNowRunning = container.isRunning();
                            log.info("kafka_listener_container_started beanName={} isRunning={}", 
                                    beanName, isNowRunning);
                            
                            if (!isNowRunning) {
                                log.warn("kafka_listener_container_failed_to_start beanName={}", beanName);
                            }
                        } else {
                            log.info("kafka_listener_container_already_running beanName={} groupId={}", 
                                    beanName, container.getGroupId());
                        }
                    }
                } catch (Exception e) {
                    log.warn("kafka_listeners_bean_inspection_skipped beanName={} reason={}", 
                            beanName, e.getClass().getSimpleName());
                }
            }
            
            log.info("kafka_listeners_startup_COMPLETE containersFound={} containersStarted={} containers=[{}]", 
                    containersFound, containersStarted, containersList.toString());
            
            if (containersFound == 0) {
                log.error("kafka_listeners_ERROR NO MessageListenerContainers found! " +
                        "Kafka listeners will NOT consume any messages. This is a critical setup failure.");
            } else if (containersStarted == 0) {
                log.warn("kafka_listeners_WARNING All containers were already running. " +
                        "This might indicate they were started before this listener ran.");
            }
        } catch (Exception e) {
            log.error("kafka_listeners_FATAL_ERROR during startup", e);
            throw e;
        }
    }
}




