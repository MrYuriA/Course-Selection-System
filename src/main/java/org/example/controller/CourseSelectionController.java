package org.example.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.exception.BusinessException;
import org.example.pojo.Result;
import org.example.pojo.SelectCourseRequest;
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
    public Result<Void> selectCourse(@RequestParam Long courseId) {
        Long studentId = UserContext.getCurrentStudentId();
        if (studentId == null) {
            throw new BusinessException(401, "未登录或Token失效");
        }
        courseSelectionService.selectCourse(studentId, courseId);
        return Result.success();
    }
}
