package org.example.pojo;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SelectCourseRequest {

    @NotNull
    Long studentId;
    @NotNull
    Long courseId;

}
