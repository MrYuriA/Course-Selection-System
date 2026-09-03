package org.example.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rabbitmq.client.Channel;                          // RabbitMQ 客户端 Channel
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.config.RabbitMQConfig;                    // 你的配置类（常量）
import org.example.dto.WaitingMessage;                       // 消息体 DTO
import org.example.pojo.CourseWaitingQueue;                  // 实体类
import org.example.mapper.CourseWaitingQueueMapper;          // MyBatis-Plus Mapper
import org.springframework.amqp.core.Message;                // Spring AMQP 消息对象
import org.springframework.amqp.rabbit.annotation.RabbitListener; // 监听注解
import org.springframework.beans.factory.annotation.Autowired;     // 依赖注入
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;                  // 组件注解

import java.io.IOException;                                  // IO 异常
import java.time.LocalDateTime;                              // 时间

@Slf4j
@Component
@RequiredArgsConstructor
public class WaitingConsumer {

    private final CourseWaitingQueueMapper waitingQueueMapper;

    //通过 Channel 发送 basicAck 命令。
    //命令中必须带上 deliveryTag，告诉 RabbitMQ “我要确认的是哪一条消息”。
    //没有 Channel，就无法发送命令；没有 deliveryTag，RabbitMQ 就不知道你要确认哪条消息。
    @RabbitListener(queues = RabbitMQConfig.WAITING_QUEUE)
    public void handleWaiting(WaitingMessage msg, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            // 1. 是否已经在排队中（status=0）
            int activeCount = waitingQueueMapper.countActiveWaiting(msg.getStudentId(), msg.getCourseId());
            if (activeCount > 0) {
                log.warn("学生已在候补队列，忽略重复消息。studentId={}, courseId={}",
                        msg.getStudentId(), msg.getCourseId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 2. 查询该学生该课程是否已有历史记录（任意状态）
            CourseWaitingQueue existing = waitingQueueMapper.selectOne(
                    new LambdaQueryWrapper<CourseWaitingQueue>()
                            .eq(CourseWaitingQueue::getStudentId, msg.getStudentId())
                            .eq(CourseWaitingQueue::getCourseId, msg.getCourseId())
            );


            int maxQueue = waitingQueueMapper.selectMaxQueueNumber(msg.getCourseId());
            int newQueue = maxQueue + 1;

            if (existing != null) {
                // 复用已有记录，重新排队
                existing.setStatus(0); // 排队中
                // 查询当前最大 queue_number + 1
                existing.setQueueNumber(newQueue);
                waitingQueueMapper.updateById(existing);
                log.info("重新激活候补记录。studentId={}, courseId={}", msg.getStudentId(), msg.getCourseId());
            } else {
                // 插入新记录
                CourseWaitingQueue record = new CourseWaitingQueue();
                record.setStudentId(msg.getStudentId());
                record.setCourseId(msg.getCourseId());
                record.setStatus(0);
                record.setQueueNumber(newQueue);
                record.setCreateTime(LocalDateTime.now());
                waitingQueueMapper.insert(record);
                log.info("插入新候补记录。studentId={}, courseId={}", msg.getStudentId(), msg.getCourseId());
            }

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("处理候补消息失败，消息重新入队。studentId={}, courseId={}",
                    msg.getStudentId(), msg.getCourseId(), e);
            channel.basicNack(deliveryTag, false, true);
        }
        //try 中处理业务，成功后 basicAck，失败 basicNack 重回队列，这就是手动 ACK 的价值。
    }
}
