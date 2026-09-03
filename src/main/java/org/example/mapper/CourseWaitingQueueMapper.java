package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.pojo.CourseWaitingQueue;

@Mapper
public interface CourseWaitingQueueMapper extends BaseMapper<CourseWaitingQueue>
{
    /**
     * 查询指定课程排队中的第一名（queue_number 最小，status=0）
     */
    @Select("SELECT * FROM course_waiting_queue " +
            "WHERE course_id = #{courseId} AND status = 0 " +
            "ORDER BY queue_number ASC LIMIT 1")
    CourseWaitingQueue selectFirstInQueue(@Param("courseId") Long courseId);

    /**
     * 查询该学生是否已经在该课程的候补队列中
     * @param studentId
     * @param courseId
     * @return 如果 count > 0，则直接返回“已在候补队列中”，不再发送消息。
     */
    @Select("SELECT COUNT(*) FROM course_waiting_queue WHERE student_id = #{studentId} AND course_id = #{courseId} AND status = 0")
    int countActiveWaiting(@Param("studentId") Long studentId, @Param("courseId") Long courseId);

    @Select("SELECT COALESCE(MAX(queue_number), 0) FROM course_waiting_queue WHERE course_id = #{courseId} AND status = 0")
    int selectMaxQueueNumber(@Param("courseId") Long courseId);
}
