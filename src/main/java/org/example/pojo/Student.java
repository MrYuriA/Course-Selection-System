package org.example.pojo;


import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("student")
@Schema(description="学生信息")
public class Student {

    @TableId(type = IdType.AUTO)
    @Schema(description = "学生ID")
    private Long id;

    @NotBlank(message = "学生姓名不能为空")
    @Size(max = 20, message = "学生姓名最多20个字符")
    @Schema(description = "学生姓名")
    private String name;

    @Schema(description = "学号")
    @NotBlank(message = "学号不能为空")
    private String studentNo;

    @Schema(description = "最大可选学分")
    @NotNull(message = "最大可选学分不能为空")
    @DecimalMin(value = "0.5", message = "学分不能低于0.5")  // 根据实际要求调整
    private BigDecimal maxCredit;

    @Schema(description = "密码")
    private String password;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableLogic
    @Schema(description = "逻辑删除标志 (0-未删除, 1-已删除)")
    private Integer deleted;

    @Schema(description = "角色 (0-学生, 1-管理员)")
    private Integer role;

}
