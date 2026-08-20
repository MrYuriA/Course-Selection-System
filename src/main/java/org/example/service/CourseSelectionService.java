package org.example.service;

import jakarta.validation.constraints.NotNull;
import org.example.enums.SelectCourseResult;

public interface CourseSelectionService {
    SelectCourseResult selectCourse(@NotNull Long studentId, @NotNull Long courseId);

    void cancelCourse(@NotNull Long studentId, @NotNull Long courseId);
}
