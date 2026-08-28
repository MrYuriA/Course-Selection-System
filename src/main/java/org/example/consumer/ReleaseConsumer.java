package org.example.consumer;

import com.rabbitmq.client.Channel;                          // RabbitMQ 客户端 Channel
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.config.RabbitMQConfig;                    // 配置类常量
import org.example.dto.ReleaseMessage;                       // 消息体 DTO
import org.example.pojo.CourseWaitingQueue;                // 实体类
import org.example.mapper.CourseWaitingQueueMapper;          // Mapper
import org.springframework.amqp.core.Message;                // Spring AMQP 消息对象
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReleaseConsumer {

    private final CourseWaitingQueueMapper waitingQueueMapper;

    @RabbitListener(queues = RabbitMQConfig.RELEASE_QUEUE)
    public void handleRelease(ReleaseMessage msg, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            // 查询该课程排队第一名
            CourseWaitingQueue first = waitingQueueMapper.selectFirstInQueue(msg.getCourseId());
            if (first != null) {
                // 更新状态为已转正（1）
                first.setStatus(1);
                waitingQueueMapper.updateById(first);
                // 这里可以打印日志模拟通知
                log.info("通知学生 {} 候补转正，课程 {}", first.getStudentId(), msg.getCourseId());
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            channel.basicNack(deliveryTag, false, true);
        }
        //try 中处理业务，成功后 basicAck，失败 basicNack 重回队列，这就是手动 ACK 的价值。

        //加锁 -- 区分业务和系统错误 增加处理逻辑 避免重复消费问题
    }
}
