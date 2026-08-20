package org.example.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    // 交换机名称
    public static final String COURSE_EXCHANGE = "course.exchange";
    // 队列名称
    public static final String WAITING_QUEUE = "course.waiting.queue";
    public static final String RELEASE_QUEUE = "course.release.queue";
    // 路由键
    public static final String WAITING_ROUTING_KEY = "course.waiting";
    public static final String RELEASE_ROUTING_KEY = "course.release";

    // 声明交换机
    @Bean
    public DirectExchange courseExchange() {
        return new DirectExchange(COURSE_EXCHANGE);
    }

    // 声明候补队列
    @Bean
    public Queue waitingQueue() {
        return new Queue(WAITING_QUEUE, true); // durable=true 持久化
    } //防止 RabbitMQ 重启后候补队列和释放队列消失

    // 声明空位释放队列
    @Bean
    public Queue releaseQueue() {
        return new Queue(RELEASE_QUEUE, true);
    }

    // 绑定候补队列到交换机
    @Bean
    public Binding waitingBinding() {
        return BindingBuilder.bind(waitingQueue())
                .to(courseExchange())
                .with(WAITING_ROUTING_KEY);
    }

    // 绑定释放队列到交换机
    @Bean
    public Binding releaseBinding() {
        return BindingBuilder.bind(releaseQueue())
                .to(courseExchange())
                .with(RELEASE_ROUTING_KEY);
    }

    /**
     * 使用 JSON 序列化消息体
     * 配置了 Jackson2JsonMessageConverter 作为统一的消息转换器。
     * 自定义了 RabbitTemplate 使用它，生产者发送 JSON。
     * 自定义了 SimpleRabbitListenerContainerFactory 使用它，消费者接收 JSON 并反序列化。
     * 总结：创建一个可以把 Java 对象序列化成 JSON 数据的工具，然后同时让生产者和消费者都使用这个工具
     * 同时设置了手动 ACK，保证消息可靠。
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    //ConnectionFactory 是 RabbitMQ 客户端提供的连接工厂，负责创建与 RabbitMQ 服务器的物理连接
    //RabbitTemplate 在发送消息时，需要一个 ConnectionFactory 来“拿到连接”，然后通过连接发送消息。
    @Bean //生产者发送消息的工具
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        // 1. 创建一个 RabbitTemplate 对象，并绑定连接工厂
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        // 2. 设置消息转换器（从容器中获取 messageConverter 实例）
        rabbitTemplate.setMessageConverter(messageConverter());
        // 3. 返回这个配置好的 RabbitTemplate 对象，放入容器
        return rabbitTemplate;
    }

    //rabbitListenerContainerFactory 就是按照给定模板不断创建用来监听标记了@RabbitListener注释的方法的容器
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
         // 1. 设置连接工厂：必须设置，否则消费者无法连接 RabbitMQ
        factory.setConnectionFactory(connectionFactory);
        // 2. 设置消息转换器：指定消费者收到 JSON 消息后，如何反序列化成 Java 对象
        //    如果不设置，默认使用 SimpleMessageConverter，它可能无法正确处理 JSON
        factory.setMessageConverter(messageConverter);
        // 3. 设置确认模式为手动 ACK
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL); // 手动 ACK
        // 4. 设置预取数量：每个消费者一次最多抓取 10 条未确认消息，控制流量
        factory.setPrefetchCount(10);
        return factory;
    }
}
