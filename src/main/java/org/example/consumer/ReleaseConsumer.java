package org.example.consumer;

import com.rabbitmq.client.Channel;                          // RabbitMQ 客户端 Channel
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.config.RabbitMQConfig;                    // 配置类常量
import org.example.dto.ReleaseMessage;                       // 消息体 DTO
import org.example.enums.SelectCourseResult;
import org.example.exception.BusinessException;
import org.example.mapper.CourseMapper;
import org.example.pojo.CourseWaitingQueue;                // 实体类
import org.example.mapper.CourseWaitingQueueMapper;          // Mapper
import org.example.service.CourseSelectionService;
import org.example.util.RedisLockUtil;
import org.springframework.amqp.core.Message;                // Spring AMQP 消息对象
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReleaseConsumer {

    private final CourseWaitingQueueMapper waitingQueueMapper;
    private final RedisLockUtil redisLockUtil;
    private final CourseSelectionService courseSelectionService;

    @RabbitListener(queues = RabbitMQConfig.RELEASE_QUEUE)
    public void handleRelease(ReleaseMessage msg, Message message, Channel channel) throws IOException {

        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String lockKey = "lock:course:" + msg.getCourseId();
        String lockValue = UUID.randomUUID().toString();
        boolean locked = false;
        try {

            // 1. 获取分布式锁，防止同一门课的多个释放消息并发处理
            locked = redisLockUtil.tryLock(lockKey, lockValue, Duration.ofSeconds(3));
            if (!locked) {
                // 获取锁失败，消息重新入队稍后重试
                channel.basicNack(deliveryTag, false, true);
                return;
            }
            // 2. 查询候补第一名
            CourseWaitingQueue first = waitingQueueMapper.selectFirstInQueue(msg.getCourseId());
            if (first == null) {
                log.info("课程 {} 无候补学生，释放消息忽略", msg.getCourseId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 3. 自动选课（业务核心）
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
                    // 业务失败（课程已满、重复选课等），记录日志但消息确认
                    log.warn("候补转正失败，学生 {} 课程 {}，原因：{}",
                            first.getStudentId(), msg.getCourseId(), result);
                }
                channel.basicAck(deliveryTag, false);
            } catch (BusinessException e) {
                // 业务异常：不是系统错误，直接确认消息，避免无限重试
                log.warn("候补转正业务异常：{}，学生 {}，课程 {}",
                        e.getMessage(), first.getStudentId(), msg.getCourseId());
                channel.basicAck(deliveryTag, false);
            }

        } catch (Exception e) {
            // 系统异常：数据库超时、网络故障等，重新入队重试
            log.error("处理释放消息系统异常", e);
            channel.basicNack(deliveryTag, false, true);
        } finally {
            // 4. 释放锁（只有自己持有才删）
            if (locked) {
                redisLockUtil.unlock(lockKey, lockValue);
            }
        }
    }
}
