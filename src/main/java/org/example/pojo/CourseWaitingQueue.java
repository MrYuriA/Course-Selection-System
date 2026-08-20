package org.example.pojo;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("course_waiting_queue")
@Schema(description = "课程等待队列")
public class CourseWaitingQueue {

    @TableId(type = IdType.AUTO)
    @Schema(description = "等待记录ID")
    private Long id;

    @NotNull(message = "学生ID不能为空")
    @Schema(description = "学生ID")
    private Long studentId;

    @NotNull(message = "课程ID不能为空")
    @Schema(description = "课程ID")
    private Long courseId;

    @NotNull(message = "排队序号不能为空")
    @Schema(description = "排队序号")
    private Integer queueNumber;

    @Schema(description = "状态：0-排队中，1-已转正，2-已取消")
    private Integer status;  // 默认0，可在数据库或插入时设置

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;


     @TableField(fill = FieldFill.INSERT_UPDATE)
     @Schema(description = "更新时间")
     private LocalDateTime updateTime;


     @TableLogic
     @Schema(description = "逻辑删除标志")
     private Integer deleted;
}