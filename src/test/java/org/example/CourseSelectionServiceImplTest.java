package org.example;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.example.dto.WaitingMessage;
import org.example.exception.BusinessException;
import org.example.mapper.CourseWaitingQueueMapper;
import org.example.mapper.CourseMapper;
import org.example.mapper.CourseSelectionMapper;
import org.example.mapper.StudentMapper;
import org.example.pojo.Course;
import org.example.pojo.CourseSelection;
import org.example.pojo.Student;
import org.example.config.RabbitMQConfig;
import org.example.enums.SelectCourseResult;
import org.example.enums.SelectionStatus;
import org.example.service.impl.CourseSelectionServiceImpl;
import org.example.util.RedisLockUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)  // 启用 Mockito
class CourseSelectionServiceImplTest {

    // 下面都是“假对象”，Mockito 会创建空壳
    @Mock
    private StudentMapper studentMapper;
    @Mock
    private CourseMapper courseMapper;
    @Mock
    private CourseSelectionMapper courseSelectionMapper;
    @Mock
    private CourseWaitingQueueMapper courseWaitingQueueMapper;
    @Mock
    private RedisLockUtil redisLockUtil;
    @Mock
    private RabbitTemplate rabbitTemplate;

    // 把假对象注入到 Service 里
    @InjectMocks
    private CourseSelectionServiceImpl courseSelectionService;

    @BeforeEach
    void setUp() {
        // 让锁总是获取成功
        when(redisLockUtil.tryLock(anyString(), anyString(), any()))
                .thenReturn(true);
        // 服务里用 registerSynchronization 注册了「事务结束后解锁」的回调，
        // 而该方法要求当前线程有活跃的事务同步。真实运行时由 @Transactional 开启，
        // 纯 Mockito 测试没有事务，所以要手动开一个。
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void testSelectCourse_WhenCourseFull_ShouldJoinWaitingQueue() {
        // 准备数据
        Long studentId = 2L;
        Long courseId = 1L;

        Student student = new Student();
        student.setId(studentId);
        student.setMaxCredit(new BigDecimal("30.0"));

        Course course = new Course();
        course.setId(courseId);
        course.setIsOpen(true);
        course.setCapacity(1);
        course.setSelectedCount(1);  // 已满
        course.setCredit(new BigDecimal("3.0"));
        course.setStartTime(LocalDateTime.now().plusHours(1));
        course.setEndTime(LocalDateTime.now().plusHours(3));

        // 告诉 Mock 对象：当调用这些方法时，返回什么
        when(studentMapper.selectById(studentId)).thenReturn(student);
        when(courseMapper.selectById(courseId)).thenReturn(course);
        when(courseWaitingQueueMapper.countActiveWaiting(studentId, courseId))
                .thenReturn(0);  // 还没排队

        // 执行测试
        SelectCourseResult result = courseSelectionService.selectCourse(studentId, courseId);

        // 断言结果
        assertEquals(SelectCourseResult.WAITING, result);

        // 验证发送了 MQ 消息,Mockito 会记录每个 Mock 对象的方法调用历史。verify 就是去查这些记录，看是否符合你的期望。
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.COURSE_EXCHANGE),
                eq(RabbitMQConfig.WAITING_ROUTING_KEY),
                any(WaitingMessage.class)
        );
    }

    @Test
    void testSelectCourse_WhenAlreadySelected_ShouldThrowBusinessException() {
        Long studentId = 1L;
        Long courseId = 1L;

        Student student = new Student();
        student.setId(studentId);
        student.setMaxCredit(new BigDecimal("30.0"));

        Course course = new Course();
        course.setId(courseId);
        course.setIsOpen(true);
        course.setCapacity(5);
        course.setSelectedCount(0);
        course.setCredit(new BigDecimal("3.0"));
        course.setStartTime(LocalDateTime.now().plusHours(1));
        course.setEndTime(LocalDateTime.now().plusHours(3));

        CourseSelection existing = new CourseSelection();
        existing.setStatus(SelectionStatus.NORMAL);  // 已正常选课

        when(studentMapper.selectById(studentId)).thenReturn(student);
        when(courseMapper.selectById(courseId)).thenReturn(course);
        when(courseSelectionMapper.selectOne(any())).thenReturn(existing);

        // 断言会抛出 BusinessException
        assertThrows(BusinessException.class, () -> {
            courseSelectionService.selectCourse(studentId, courseId);
        });
    }

    /**
     * 防回归：预检查时看起来还有名额，但真正做原子扣减时被别人抢完了。
     * 此时必须转候补，且绝不能留下一条「已选中」的幽灵记录。
     */
    @Test
    void testSelectCourse_WhenAtomicDeductFails_ShouldJoinWaitingQueueWithoutWritingRecord() {
        Long studentId = 3L;
        Long courseId = 1L;

        Student student = new Student();
        student.setId(studentId);
        student.setMaxCredit(new BigDecimal("30.0"));

        Course course = new Course();
        course.setId(courseId);
        course.setIsOpen(true);
        course.setCapacity(10);
        course.setSelectedCount(9);  // 预检查看到还有 1 个名额
        course.setCredit(new BigDecimal("3.0"));
        course.setStartTime(LocalDateTime.now().plusHours(1));
        course.setEndTime(LocalDateTime.now().plusHours(3));

        when(studentMapper.selectById(studentId)).thenReturn(student);
        when(courseMapper.selectById(courseId)).thenReturn(course);
        when(courseSelectionMapper.selectOne(any())).thenReturn(null);  // 没选过
        when(courseMapper.update(any(), any())).thenReturn(0);          // 扣减时名额已被抢完
        when(courseWaitingQueueMapper.countActiveWaiting(studentId, courseId)).thenReturn(0);

        SelectCourseResult result = courseSelectionService.selectCourse(studentId, courseId);

        assertEquals(SelectCourseResult.WAITING, result);
        // 关键断言：扣减失败时不得写入任何选课记录
        verify(courseSelectionMapper, never()).insert(any(CourseSelection.class));
        verify(courseSelectionMapper, never()).updateById(any(CourseSelection.class));
    }

    @Test
    void testCancelCourse_ShouldUpdateStatusAndSendReleaseMessage() {
        Long studentId = 1L;
        Long courseId = 1L;

        // 准备数据
        CourseSelection selection = new CourseSelection();
        selection.setId(1L);
        selection.setStudentId(studentId);
        selection.setCourseId(courseId);
        selection.setStatus(SelectionStatus.NORMAL);

        Course course = new Course();
        course.setId(courseId);
        course.setName("Java入门");
        course.setSelectedCount(1);
        course.setCapacity(1);

        // Mock 行为
        when(courseSelectionMapper.selectOne(any(Wrapper.class))).thenReturn(selection);
        when(courseMapper.selectById(courseId)).thenReturn(course);
        when(courseSelectionMapper.updateById(selection)).thenReturn(1);
        when(courseMapper.update(any(), any())).thenReturn(1);

        // 执行退课
        courseSelectionService.cancelCourse(studentId, courseId);

        // 断言：选课状态已更新为已退
        assertEquals(SelectionStatus.WITHDRAWN, selection.getStatus());

        // 验证选课记录被更新
        verify(courseSelectionMapper, times(1)).updateById(selection);

        // 验证课程人数被扣减
        verify(courseMapper, times(1)).update(any(), any());
    }
}
