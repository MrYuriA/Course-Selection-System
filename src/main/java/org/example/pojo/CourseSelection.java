package org.example.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.enums.SelectionStatus;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("course_selection")
@Schema(description = "选课信息")
public class CourseSelection {

    @TableId(type = IdType.AUTO)
    @Schema(description = "ID")
    private Long id;

    @Schema(description = "学生ID")
    private Long studentId;

    @Schema(description = "课程ID")
    private Long courseId;

    @Schema(description = "选课状态 (0正常(已选) / 1已退)")
    private SelectionStatus status;

    @Schema(description = "选课时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableLogic
    @Schema(description = "逻辑删除标志 (0-未删除, 1-已删除)")
    private Integer deleted;

}
