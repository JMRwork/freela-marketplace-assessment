package br.com.freela.contrato.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfiguration {
    @Bean
    public NewTopic contratoEventosTopic(
            @Value("${app.kafka.topics.contrato-eventos}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
