package org.example.service;

import org.example.pojo.Course;
import org.example.pojo.CourseQueryParam;
import org.example.pojo.PageResult;

public interface CourseService {

    /**
     * 根据ID查询课程
     */
    Course getCourseInfo(Long id);

    /**
     * 新增课程
     */
    void addCourseInfo(Course course);

    /**
     * 修改课程
     */
    void updateCourseInfo(Long id, Course course);

    /**
     * 删除课程（逻辑删除）
     */
    void delCourseInfo(Long id);

    /**
     * 条件分页查询
     */
    PageResult<Course> getPageInfo(CourseQueryParam courseQueryParam);
}