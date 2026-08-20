package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.enums.SelectCourseResult;
import org.example.exception.BusinessException;
import org.example.pojo.Result;
import org.example.service.CourseSelectionService;
import org.example.util.UserContext;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@Tag(name = "课程选课模块")
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseSelectionController {


    private  final CourseSelectionService courseSelectionService;

    @PostMapping("/select")
    @Operation(summary = "选课")
    public Result<Void> selectCourse(@RequestParam Long courseId) {

        Long studentId = UserContext.getCurrentStudentId();

        if (studentId == null) {
            throw new BusinessException(401, "未登录或Token失效");
        }

        log.info("选课请求，学生ID：{}，课程ID：{}", studentId, courseId);
        SelectCourseResult result = courseSelectionService.selectCourse(studentId, courseId);

        if (result == SelectCourseResult.SUCCESS) {
            return Result.successWithMsg("选课成功");
        } else if (result == SelectCourseResult.WAITING) {
            return Result.successWithMsg("课程已满，已加入候补队列");
        } else if (result == SelectCourseResult.ALREADY_WAITING) {
            return Result.successWithMsg("您已在候补队列中，请耐心等待");
        } else {
            // 兜底：未知结果（比如 FAIL）
            return Result.error("选课失败，请稍后重试");
        }
    }

    @PostMapping("/cancel")
    @Operation(summary = "退课")
    public Result<Void> cancelCourse(@RequestParam Long courseId) {
        Long studentId = UserContext.getCurrentStudentId();
        if (studentId == null) {
            throw new BusinessException(401, "未登录或Token失效");
        }
        log.info("退课请求，学生ID：{}，课程ID：{}", studentId, courseId);
        courseSelectionService.cancelCourse(studentId, courseId);
        return Result.success();
    }
}
