package dev.abu.screener_backend.rabbitmq;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public AmqpAdmin amqpAdmin(CachingConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

}
