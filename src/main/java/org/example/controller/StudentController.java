package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.pojo.*;
import org.example.service.StudentService;
import org.springframework.web.bind.annotation.*;
@Slf4j
@RestController
@Tag(name="学生管理模块")
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {


    private final StudentService studentService;

    @PostMapping("/login")
    @Operation(summary = "学生登录")
    public Result<String> login(@Valid @RequestBody StudentLoginParam param) {
        log.info("学生登录请求，学号：{}", param.getStudentNo());
        String token = studentService.login(param);
        return Result.success(token);
    }

    @PostMapping("/register")
    @Operation(summary = "学生注册")
    public Result<Void> register(@Valid @RequestBody StudentRegisterParam param) {
        log.info("学生注册请求，学号：{}", param.getStudentNo());
        studentService.register(param);
        return Result.success();
    }


    @GetMapping("/{id}")
    @Operation(summary="根据ID查询学生")
    public Result<Student> getInfo(@Parameter(description="学生ID")@PathVariable Long id) {
        log.info("查询学生请求，学生id：{}", id);
        Student userInfo = studentService.getStudentInfo(id);
        return Result.success(userInfo);
    }

    @GetMapping
    @Operation(summary="分页查询学生")
    public Result<PageResult<Student>> getPage(@Valid @Parameter(description="分页查询参数")StuQueryParam stuQueryParam) {
        log.info("分页查询学生请求，page={}, pageSize={}, 姓名={}, 学号={}",
                stuQueryParam.getPage(),
                stuQueryParam.getPageSize(),
                stuQueryParam.getName(),
                stuQueryParam.getStudentNo());
        PageResult<Student> pageInfo = studentService.getPageInfo(stuQueryParam);
        return Result.success(pageInfo);
    }

    @PostMapping
    @Operation(summary="添加学生")
    public Result<Void> addInfo(@Valid @Parameter(description="添加的学生信息")@RequestBody Student student) {
        log.info("添加学生请求，学号：{}, 姓名：{}", student.getStudentNo(), student.getName());
        studentService.addStudentInfo(student);
        return Result.success();
    }

    @PutMapping("/{id}")
    @Operation(summary="根据路径ID修改学生")
    public Result<Void> updateInfo (@Parameter(description="学生ID")@PathVariable Long id,@Valid @Parameter(description="要修改的用户信息") @RequestBody Student student) {
        log.info("修改学生请求，路径ID：{}, 学号：{}, 姓名：{}", id, student.getStudentNo(), student.getName());
        studentService.updateStudentInfo(id, student);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary="根据ID删除学生")
    public Result<Void> delInfo(@Parameter(description="学生ID")@PathVariable Long id) {
        log.info("删除学生请求，学生id：{}", id);
        studentService.delStudentInfo(id);
        return Result.success();
    }
}
