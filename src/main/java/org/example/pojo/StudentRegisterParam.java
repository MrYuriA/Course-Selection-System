package org.example.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StudentRegisterParam {
    @NotBlank(message = "学号不能为空")
    private String studentNo;

    @NotBlank(message = "学生姓名不能为空")
    @Size(max = 20, message = "学生姓名最多20个字符")
    private String name;

    @NotBlank(message = "密码不能为空")
    private String password;
}
