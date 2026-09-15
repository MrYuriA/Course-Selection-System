package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;  // 别忘了加这个
import org.example.config.RabbitMQConfig;
import org.example.dto.ReleaseMessage;
import org.example.dto.WaitingMessage;
import org.example.enums.SelectCourseResult;
import org.example.enums.SelectionStatus;
import org.example.exception.BusinessException;
import org.example.mapper.CourseMapper;
import org.example.mapper.CourseSelectionMapper;
import org.example.mapper.CourseWaitingQueueMapper;
import org.example.mapper.StudentMapper;
import org.example.pojo.Course;
import org.example.pojo.CourseSelection;
import org.example.pojo.Student;
import org.example.service.CourseSelectionService;
import org.example.util.RedisLockUtil;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

@Slf4j  // 类上加这个注解
@Service
@RequiredArgsConstructor
public class CourseSelectionServiceImpl implements CourseSelectionService {

    private final StudentMapper studentMapper;
    private final CourseMapper courseMapper;
    private final CourseSelectionMapper courseSelectionMapper;
    private final RedisLockUtil redisLockUtil;
    private final CourseWaitingQueueMapper courseWaitingQueueMapper;
    private final RabbitTemplate rabbitTemplate;

    //RabbitMQ：选课满分支没有数据库写操作，发消息不受事务影响；退课有写操作，必须等事务提交后再发消息，避免数据不一致。

    @Override
    @Transactional(rollbackFor = Exception.class)
    //发生任何异常都会进行操作回滚，对于判断执行顺序可以放宽松,但仍建议校验在先,执行在后,减少不必要的回滚节省数据库资源
    public SelectCourseResult selectCourse(Long studentId, Long courseId) {

        if (studentId == null || courseId == null) {
            throw new BusinessException("学生ID和课程ID不能为空");
        }

        String lockKey = "lock:course:" + courseId;
        String lockValue = UUID.randomUUID().toString();

        // 1. 检查学生是否存在
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            log.warn("选课失败：学生不存在。学生ID: {}", studentId);  // ← 加 WARN
            throw new BusinessException("学生不存在");
        }

        // 2. 检查课程是否存在、是否开放、容量是否已满
        Course course = courseMapper.selectById(courseId);
        if (course == null || !(course.getIsOpen())) {
            log.warn("选课失败：课程不存在或未开放。课程ID: {}", courseId);  // ← 加 WARN
            throw new BusinessException("课程不存在或未开放");
        }


        // 3. 时间冲突检查
        int conflictCount = courseSelectionMapper.countTimeConflict(studentId,
                course.getStartTime(), course.getEndTime());
        if (conflictCount > 0) {
            log.warn("选课失败：与已选课程时间冲突。学生ID: {}, 课程ID: {}, 新课时间: {} - {}",
                    studentId, courseId, course.getStartTime(), course.getEndTime());  // ← 加 WARN
            throw new BusinessException("与已选课程时间冲突");
        }

        // 4. 学分上限校验
        int currentCredits = courseSelectionMapper.sumSelectedCredits(studentId);

        BigDecimal currentCreditsDecimal = BigDecimal.valueOf(currentCredits);
        BigDecimal estimatedTotal = course.getCredit().add(currentCreditsDecimal);

        if (estimatedTotal.compareTo(student.getMaxCredit()) > 0) {
            log.warn("选课失败：超过学分上限。学生ID: {}, 已选学分: {}, 新课学分: {}, 学分上限: {}",
                    studentId, currentCredits, course.getCredit(), student.getMaxCredit());  // ← 加 WARN
            throw new BusinessException("超过学分上限");
        }



        // 尝试获取锁，最多等待3秒
        boolean locked = redisLockUtil.tryLock(lockKey, lockValue, Duration.ofSeconds(3));

        if (!locked) {
            log.warn("选课失败：系统繁忙，请稍后重试。课程ID：{}", courseId);
            throw new BusinessException("系统繁忙，请稍后重试");
        }

        // 注册事务回调：事务提交/回滚后释放锁
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                Long result = redisLockUtil.unlock(lockKey, lockValue);
                if (result == null || result == 0) {
                    log.warn("释放锁失败，key: {}, value: {}, 可能锁已过期或被他人持有", lockKey, lockValue);
                } else {
                    log.debug("释放锁成功，key: {}", lockKey);
                }
            }
        });

        // 1. 拿到锁后再查一次，用于展示当前容量、快速分流
        // 注意：受 REPEATABLE READ 快照影响，这次读到的可能仍是拿锁之前的旧值，
        // 所以「还有没有名额」最终以第 4 步的数据库原子扣减为准。
        course = courseMapper.selectById(courseId);
        if (course == null || !(course.getIsOpen())) {
            log.warn("选课失败：课程不存在或未开放。课程ID: {}", courseId);  // ← 加 WARN
            throw new BusinessException("课程不存在或未开放");
        }

        // 2. 明显已满时不必再走扣减，直接排队
        // 该分支内只发 MQ 消息、不再写库，所以消息不受后续回滚影响。
        if (course.getSelectedCount() >= course.getCapacity()) {
            log.warn("选课失败：课程容量已满。课程ID: {}, 当前人数: {}, 容量: {}",
                    courseId, course.getSelectedCount(), course.getCapacity());  // ← 课程已满,加入排队队列
            return joinWaitingQueue(studentId, courseId);
        }

        // 3. 防止重复选课
        CourseSelection existing = courseSelectionMapper.selectOne(
                new LambdaQueryWrapper<CourseSelection>()
                        .eq(CourseSelection::getStudentId, studentId)
                        .eq(CourseSelection::getCourseId, courseId)
        );
        if (existing != null && SelectionStatus.NORMAL.equals(existing.getStatus())) {
            log.warn("选课失败：重复选课。学生ID: {}, 课程ID: {}", studentId, courseId);
            throw new BusinessException("请勿重复选课");
        }

        // 4. 先由数据库原子扣减决定成败：影响行数为 0 说明名额已被抢完，转候补。
        // 此刻尚未产生任何写操作，所以正常返回、事务提交也不会留下垃圾数据。
        int update = courseMapper.update(null, new LambdaUpdateWrapper<Course>()
                .setSql("selected_count = selected_count + 1")
                .eq(Course::getId, courseId)
                .apply("selected_count < capacity")
        );
        if (update == 0) {
            log.warn("并发冲突，转入候补。studentId={}, courseId={}", studentId, courseId);
            return joinWaitingQueue(studentId, courseId);  // 不报错，转候补
        }

        // 5. 扣减成功后再落选课记录：曾经退课的重新激活，否则插入新记录
        if (existing != null) {
            existing.setStatus(SelectionStatus.NORMAL);
            courseSelectionMapper.updateById(existing);
            log.info("重新激活选课记录。学生ID: {}, 课程ID: {}", studentId, courseId);
        } else {
            CourseSelection selection = new CourseSelection();
            selection.setStudentId(studentId);
            selection.setCourseId(courseId);
            selection.setStatus(SelectionStatus.NORMAL);
            courseSelectionMapper.insert(selection);
        }

        log.info("选课成功。学生ID: {}, 课程ID: {}, 课程名: {}, 学分: {}",
                studentId, courseId, course.getName(), course.getCredit());  // ← 加 INFO
        return SelectCourseResult.SUCCESS;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelCourse(Long studentId, Long courseId) {

        if (studentId == null || courseId == null) {
            throw new BusinessException("学生ID和课程ID不能为空");
        }

        String lockKey = "lock:course:" + courseId;
        String lockValue = UUID.randomUUID().toString();

        // 1. 查询该学生的正常选课记录
        CourseSelection selection = courseSelectionMapper.selectOne(
                new LambdaQueryWrapper<CourseSelection>()
                        .eq(CourseSelection::getStudentId, studentId)
                        .eq(CourseSelection::getCourseId, courseId)
                        .eq(CourseSelection::getStatus, SelectionStatus.NORMAL)
        );
        if (selection == null) {
            log.warn("退课失败：选课记录不存在或已退课。学生ID: {}, 课程ID: {}", studentId, courseId);
            throw new BusinessException("选课记录不存在或已退课");
        }

        // 2. 检查课程是否存在（可选，但建议校验）
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }

        // 尝试获取锁，最多等待3秒
        boolean locked = redisLockUtil.tryLock(lockKey, lockValue, Duration.ofSeconds(3));

        if (!locked) {
            log.warn("退课失败：系统繁忙，请稍后重试。课程ID：{}", courseId);
            throw new BusinessException("系统繁忙，请稍后重试");
        }

        // 注册事务回调：事务提交/回滚后释放锁
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                Long result = redisLockUtil.unlock(lockKey, lockValue);
                if (result == null || result == 0) {
                    log.warn("释放锁失败，key: {}, value: {}, 可能锁已过期或被他人持有", lockKey, lockValue);
                } else {
                    log.debug("释放锁成功，key: {}", lockKey);
                }
            }
        });

            // 3. 更新选课状态为已退
            selection.setStatus(SelectionStatus.WITHDRAWN);
            courseSelectionMapper.updateById(selection);

            // 4. 课程已选人数减1（不能小于0）
            int update = courseMapper.update(null,
                    new LambdaUpdateWrapper<Course>()
                            .setSql("selected_count = selected_count - 1")
                            .eq(Course::getId, courseId)
                            .apply("selected_count > 0")  // 防止减成负数
            );
            if (update == 0) {
                log.error("退课异常：课程已选人数已为0。课程ID: {}", courseId);
                throw new BusinessException("退课失败，课程人数异常");
            }

            log.info("退课成功。学生ID: {}, 课程ID: {}, 课程名: {}", studentId, courseId, course.getName());

        // 在事务提交后发送释放消息
        //  Spring 的事务管理器在事务提交前后提供了一些回调点，你可以注册一个 TransactionSynchronization 对象
        //  afterCommit() 方法会在事务成功提交后由 Spring 自动调用,达到了“事务提交后执行”的目的。
        // 为什么这么做:因为方法返回后事务才提交，在方法内部发送时事务还没提交。Spring 没有直接提供“事务提交后执行”的注解，所以需要使用这个回调机制。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ReleaseMessage msg = new ReleaseMessage();
                msg.setCourseId(courseId);
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.COURSE_EXCHANGE,
                        RabbitMQConfig.RELEASE_ROUTING_KEY,
                        msg
                );
                log.info("已发送空位释放消息。courseId={}", courseId);
            }
        });
    }

    /**
     * 课程已满时自动加入候补队列
     */
    private SelectCourseResult joinWaitingQueue(Long studentId, Long courseId) {
        // 1. 检查是否已在候补队列（status=0）
        int count = courseWaitingQueueMapper.countActiveWaiting(studentId, courseId);
        if (count > 0) {
            log.warn("学生已在候补队列中，无需重复加入。studentId={}, courseId={}", studentId, courseId);
            return SelectCourseResult.ALREADY_WAITING;
        }

        // 2. 发送 MQ 消息
        WaitingMessage msg = new WaitingMessage();
        msg.setStudentId(studentId);
        msg.setCourseId(courseId);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.COURSE_EXCHANGE,
                RabbitMQConfig.WAITING_ROUTING_KEY,
                msg
        );
        log.info("已发送候补消息。studentId={}, courseId={}", studentId, courseId);
        return SelectCourseResult.WAITING;
    }
}