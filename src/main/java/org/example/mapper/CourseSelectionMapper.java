package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.pojo.CourseSelection;

import java.time.LocalDateTime;

@Mapper
public interface CourseSelectionMapper extends BaseMapper<CourseSelection> {

    // 时间冲突统计
    @Select("SELECT COUNT(*) FROM course_selection cs " +
            "JOIN course c ON cs.course_id = c.id " +
            "WHERE cs.student_id = #{studentId} AND cs.status = 0 " +
            "AND c.start_time < #{endTime} " +
            "AND c.end_time > #{startTime}" )
    int countTimeConflict(@Param("studentId") Long studentId,
                          @Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime);

    // 统计已选学分总和（仅正常状态）
    @Select("SELECT IFNULL(SUM(c.credit), 0) FROM course_selection cs " +
            "JOIN course c ON cs.course_id = c.id " +
            "WHERE cs.student_id = #{studentId} AND cs.status = 0")
    int sumSelectedCredits(@Param("studentId") Long studentId);
}
