package com.opslens.application.analysis;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnalysisJobQueueConfig {

    public static final String EXCHANGE_NAME = "opslens.analysis";
    public static final String QUEUE_NAME = "opslens.analysis.jobs";
    public static final String ROUTING_KEY = "analysis.requested";

    @Bean
    Queue analysisJobQueue() {
        return new Queue(QUEUE_NAME, true);
    }

    @Bean
    DirectExchange analysisJobExchange() {
        return new DirectExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    Binding analysisJobBinding(Queue analysisJobQueue, DirectExchange analysisJobExchange) {
        return BindingBuilder.bind(analysisJobQueue)
            .to(analysisJobExchange)
            .with(ROUTING_KEY);
    }

    @Bean
    MessageConverter analysisJobMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
