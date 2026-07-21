package org.example.service;

import jakarta.validation.constraints.NotNull;

public interface CourseSelectionService {
    void selectCourse(@NotNull Long studentId, @NotNull Long courseId);
}
