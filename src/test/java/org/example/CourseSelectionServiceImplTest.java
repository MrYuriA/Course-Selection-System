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
import org.example.service.impl.CourseSelectionServiceImpl;
import org.example.util.RedisLockUtil;
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
import org.example.enums.SelectionStatus;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.example.enums.SelectionStatus;

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

        // 验证发送了 MQ 消息
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

        // 初始化事务同步，防止 registerSynchronization 抛异常
        TransactionSynchronizationManager.initSynchronization();
        try {
            // 执行退课
            courseSelectionService.cancelCourse(studentId, courseId);

            // 断言：选课状态已更新为已退
            assertEquals(SelectionStatus.WITHDRAWN, selection.getStatus());

            // 验证选课记录被更新
            verify(courseSelectionMapper, times(1)).updateById(selection);

            // 验证课程人数被扣减
            verify(courseMapper, times(1)).update(any(), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}