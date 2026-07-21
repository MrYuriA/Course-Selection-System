package org.example.pojo;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("course")
@Schema(description = "课程信息")
public class Course {

    @TableId(type = IdType.AUTO)
    @Schema(description = "课程ID")
    private Long id;

    @NotBlank(message = "课程名称不能为空")
    @Size(max = 50, message = "课程名称最多50个字符")
    @Schema(description = "课程名称")
    private String name;

    @Size(max = 20, message = "教师姓名最多20个字符")
    @Schema(description = "授课教师姓名")
    private String teacherName;                         // 允许为空，只限制长度

    @NotNull(message = "学分不能为空")
    @DecimalMin(value = "0.5", message = "学分不能低于0.5")
    @DecimalMax(value = "10.0", message = "学分不能超过10")
    @Schema(description = "学分")
    private BigDecimal credit;

    @NotNull(message = "课程容量不能为空")
    @Min(value = 1, message = "容量至少为1")
    @Max(value = 500, message = "容量不能超过500")
    @Schema(description = "课程容量")
    private Integer capacity;

    @Schema(description = "已选人数")
    private Integer selectedCount;                      // 系统维护，前端不传，不加校验

    @NotNull(message = "开始时间不能为空")
    @Future(message = "开始时间必须在将来")
    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    @Future(message = "结束时间必须在将来")
    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Size(max = 30, message = "教室名称最多30个字符")
    @Schema(description = "教室")
    private String classroom;

    @Size(max = 20, message = "学期名称最多20个字符")
    @Schema(description = "学期")
    private String semester;

    @Schema(description = "是否开放选课 (0-关闭, 1-开放)")
    private Boolean isOpen;              // 对应 is_open

    @Schema(description = "开放选课时间")
    private LocalDateTime openTime;      // 对应 open_time

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;    // 对应 create_time

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;    // 对应 update_time

    @TableLogic
    @Schema(description = "逻辑删除标志 (0-未删除, 1-已删除)")
    private Integer deleted;
}