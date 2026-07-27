package org.example.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.example.util.PageUtil;

import java.math.BigDecimal;

@Data
@Schema(description = "课程分页查询参数")
public class CourseQueryParam {

    @Schema(description = "页码", example = "1")
    @Min(value = 1, message = "页码最小为1")
    private Integer page = 1;

    @Schema(description = "每页记录数", example = "10")
    @Min(value = 1, message = "每页记录数最小为1")
    @Max(value = 100, message = "每页记录数最大为100")
    private Integer pageSize = PageUtil.DEFAULT_PAGE_SIZE;

    @Schema(description = "课程ID")
    private Long id;                                    // 选填，不校验

    @Schema(description = "课程名称")
    @Size(max = 50, message = "课程名称最多50个字符")
    private String name;                                // 选填，只限制长度

    @Schema(description = "授课教师")
    @Size(max = 20, message = "教师姓名最多20个字符")
    private String teacherName;                         // 选填，只限制长度

    @Schema(description = "学分")
    @DecimalMin(value = "0.5", message = "学分不能低于0.5")
    @DecimalMax(value = "10.0", message = "学分不能超过10")
    private BigDecimal credit;                          // 选填，但如果传了就得在合理范围

    @Schema(description = "教室")
    @Size(max = 30, message = "教室名称最多30个字符")
    private String classroom;                           // 选填，只限制长度

    @Schema(description = "学期")
    @Size(max = 20, message = "学期名称最多20个字符")
    private String semester;                            // 选填，只限制长度

    @Schema(description = "是否开放选课 (0-关闭, 1-开放)")
    private Boolean isOpen;                             // 布尔值，不需要校验

    @Schema(description = "排序字段，如 id、name、teacherName、credit")
    @Pattern(regexp = "^(|id|name|teacherName|credit|capacity|startTime|endTime|classroom|semester)$",
            message = "排序字段不合法")
    private String sortField;

    @Schema(description = "排序方向，asc 或 desc")
    @Pattern(regexp = "^(|asc|desc)$", message = "排序方向只能为 asc 或 desc")
    private String sortOrder;
}