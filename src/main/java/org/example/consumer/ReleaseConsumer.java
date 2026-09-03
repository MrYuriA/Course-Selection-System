package org.example.consumer;

import com.rabbitmq.client.Channel;                          // RabbitMQ 客户端 Channel
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.config.RabbitMQConfig;                    // 配置类常量
import org.example.dto.ReleaseMessage;                       // 消息体 DTO
import org.example.enums.SelectCourseResult;
import org.example.exception.BusinessException;
import org.example.pojo.CourseWaitingQueue;                // 实体类
import org.example.mapper.CourseWaitingQueueMapper;          // Mapper
import org.example.service.CourseSelectionService;
import org.springframework.amqp.core.Message;                // Spring AMQP 消息对象
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.io.IOException;

// 一开始在释放消费者和选课方法都加了同一把课程级 Redis 锁，后来发现这是不可重入锁，释放消费者拿到锁后再调选课方法会自锁。
// 所以改成在释放消费者中不重复加锁，直接调用选课方法，由选课方法内部的锁来保证并发安全。避免自锁，保持锁粒度统一。

@Slf4j
@Component
@RequiredArgsConstructor
public class ReleaseConsumer {

    private final CourseWaitingQueueMapper waitingQueueMapper;
    private final CourseSelectionService courseSelectionService;


    @RabbitListener(queues = RabbitMQConfig.RELEASE_QUEUE)
    public void handleRelease(ReleaseMessage msg, Message message, Channel channel) throws IOException {

        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            // 1. 查询候补第一名
            CourseWaitingQueue first = waitingQueueMapper.selectFirstInQueue(msg.getCourseId());
            if (first == null) {
                log.info("课程 {} 无候补学生，释放消息忽略", msg.getCourseId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 2. 自动选课（业务核心）
            try {
                SelectCourseResult result = courseSelectionService.selectCourse(
                        first.getStudentId(), msg.getCourseId());

                if (result == SelectCourseResult.SUCCESS) {
                    // 选课成功，更新候补状态为已转正
                    first.setStatus(1);
                    waitingQueueMapper.updateById(first);
                    log.info("候补转正成功，学生 {} 已自动选课，课程 {}",
                            first.getStudentId(), msg.getCourseId());
                } else {
                    log.warn("候补转正失败，学生 {} 课程 {}，原因：{}",
                            first.getStudentId(), msg.getCourseId(), result);
                }
                channel.basicAck(deliveryTag, false);
            } catch (BusinessException e) {
                // 业务异常：直接确认，避免死循环
                log.warn("候补转正业务异常：{}，学生 {}，课程 {}",
                        e.getMessage(), first.getStudentId(), msg.getCourseId());
                channel.basicAck(deliveryTag, false);  // ✅ 补上 ACK
            }

        } catch (Exception e) {
            // 系统异常：重新入队重试
            log.error("处理释放消息系统异常", e);
            channel.basicNack(deliveryTag, false, true);  // ✅ 补上 NACK
        }
    }
}
