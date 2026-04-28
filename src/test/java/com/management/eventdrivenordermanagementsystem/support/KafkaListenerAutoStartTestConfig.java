package com.management.eventdrivenordermanagementsystem.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Test configuration that GUARANTEES Kafka listener containers are started.
 * 
 * This listens for Spring context refresh and explicitly starts all
 * MessageListenerContainer beans that are not already running.
 * 
 * This is a last-resort approach when standard auto-startup doesn't work.
 */
@TestConfiguration
public class KafkaListenerAutoStartTestConfig {
    private static final Logger log = LoggerFactory.getLogger(KafkaListenerAutoStartTestConfig.class);

    /**
     * Component that auto-starts Kafka listeners after context is fully loaded.
     * Listens for ContextRefreshedEvent which fires AFTER all beans are initialized
     * and configured.
     */
    @Component
    public static class KafkaListenerAutoStarter {
        private static final Logger log = LoggerFactory.getLogger(KafkaListenerAutoStarter.class);

        @EventListener
        public void onContextRefresh(ContextRefreshedEvent event) {
            log.info("kafka_auto_starter_context_refreshed starting all message listener containers");
            
            try {
                var context = event.getApplicationContext();
                String[] containerNames = context.getBeanNamesForType(MessageListenerContainer.class);
                
                log.info("kafka_auto_starter_found_containers count={}", containerNames.length);
                
                int started = 0;
                for (String beanName : containerNames) {
                    try {
                        MessageListenerContainer container = context.getBean(beanName, MessageListenerContainer.class);
                        
                        if (!container.isRunning()) {
                            log.info("kafka_auto_starter_starting_container beanName={} groupId={}", 
                                    beanName, container.getGroupId());
                            container.start();
                            started++;
                            
                            // Verify it started
                            if (container.isRunning()) {
                                log.info("kafka_auto_starter_container_started beanName={} confirmed_running=true", beanName);
                            } else {
                                log.error("kafka_auto_starter_container_failed beanName={} confirmed_running=false", beanName);
                            }
                        } else {
                            log.info("kafka_auto_starter_container_already_running beanName={}", beanName);
                        }
                    } catch (Exception e) {
                        log.warn("kafka_auto_starter_error_starting_container beanName={} error={}", beanName, e.getMessage());
                    }
                }
                
                log.info("kafka_auto_starter_complete started={} total={}", started, containerNames.length);
                
            } catch (Exception e) {
                log.error("kafka_auto_starter_fatal_error", e);
            }
        }
    }
}

