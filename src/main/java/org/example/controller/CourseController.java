package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.pojo.Course;
import org.example.pojo.CourseQueryParam;
import org.example.pojo.PageResult;
import org.example.pojo.Result;
import org.example.service.CourseService;
import org.example.util.PageUtil;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@Tag(name = "课程管理模块")
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {


    private final CourseService courseService;

    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询课程")
    public Result<Course> getInfo(@Parameter(description = "课程ID") @PathVariable Long id) {
        log.info("查询课程请求，课程id：{}", id);
        Course course = courseService.getCourseInfo(id);
        return Result.success(course);
    }

    @GetMapping
    @Operation(summary = "分页查询课程")
    public Result<PageResult<Course>> getPage(@Valid @Parameter(description = "分页查询参数") CourseQueryParam courseQueryParam) {

        // 关键：对 pageSize 做范围限制
        int safePageSize = PageUtil.validPageSize(courseQueryParam.getPageSize());
        courseQueryParam.setPageSize(safePageSize);

        log.info("分页查询课程请求，page={}, pageSize={}, 课程名称={}, 授课教师={}",
                courseQueryParam.getPage(),
                courseQueryParam.getPageSize(),
                courseQueryParam.getName(),
                courseQueryParam.getTeacherName());

        PageResult<Course> pageInfo = courseService.getPageInfo(courseQueryParam);
        return Result.success(pageInfo);
    }

    @PostMapping
    @Operation(summary = "添加课程")
    public Result<Void> addInfo(@Valid @Parameter(description = "添加的课程信息") @RequestBody Course course) {
        log.info("添加课程请求，课程名称={}, 授课教师={}", course.getName(), course.getTeacherName());
        courseService.addCourseInfo(course);
        return Result.success();
    }

    @PutMapping("/{id}")
    @Operation(summary = "根据路径ID修改课程")
    public Result<Void> updateInfo(@Parameter(description = "课程ID") @PathVariable Long id,
                             @Parameter(description = "要修改的课程信息") @RequestBody @Valid Course course) {
        log.info("修改课程请求，课程id：{}，课程名称={}, 授课教师={}", id,course.getName(), course.getTeacherName());
        courseService.updateCourseInfo(id, course);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "根据ID删除课程")
    public Result<Void> delInfo(@Parameter(description = "课程ID") @PathVariable Long id) {
        log.info("删除课程请求，课程id：{}", id);
        courseService.delCourseInfo(id);
        return Result.success();
    }
}