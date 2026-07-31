package org.example.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.example.util.PageUtil;

import java.math.BigDecimal;

@Data
@Schema(description = "学生分页查询参数")
public class StuQueryParam {

    //Student（实体/业务对象）：对应数据库表或业务核心，添加 @NotNull、@Size 等严格校验，确保业务数据完整性（如姓名不能为空、年龄必须大于0）。

    //StudentPageQuery（查询对象）：只对“分页基础参数”添加非空校验，对“过滤条件”完全不添加 @NotNull 或 @NotBlank。
    //核心逻辑：在 Service 层处理查询时，直接判断 if (studentName != null) 来决定是否拼接该条件

    @Schema(description = "页码", example = "1")
    @Min(value = 1, message = "页码最小为1")
    private Integer page = 1;

    @Schema(description = "每页记录数", example = "10")
    @Min(value = 1, message = "每页记录数最小为1")
    @Max(value = 100, message = "每页记录数最大为100")
    private Integer pageSize = PageUtil.DEFAULT_PAGE_SIZE;

    @Schema(description = "学生ID")
    private Long id;

    @Size(max = 20, message = "学生姓名最多20个字符")
    @Schema(description = "学生姓名")
    private String name;

    @Schema(description = "学号")
    private String studentNo;

    @Schema(description = "最大可选学分")
    @DecimalMin(value = "0.5", message = "学分不能低于0.5")  // 根据实际要求调整
    private BigDecimal maxCredit;

    @Schema(description = "排序字段，如 id、name、teacherName、credit")
    @Pattern(regexp = "^(id|name|teacherName|credit|capacity|startTime|endTime|classroom|semester)$",
            message = "排序字段不合法")
    private String sortField;

    @Schema(description = "排序方向，asc 或 desc")
    @Pattern(regexp = "^(asc|desc)$", message = "排序方向只能为 asc 或 desc")
    private String sortOrder;
}