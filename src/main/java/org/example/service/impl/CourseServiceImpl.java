package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.exception.BusinessException;
import org.example.mapper.CourseMapper;
import org.example.pojo.Course;
import org.example.pojo.CourseQueryParam;
import org.example.pojo.PageResult;
import org.example.service.CourseService;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private  final CourseMapper courseMapper;

    @Override
    public Course getCourseInfo(Long id) {

        Course course = courseMapper.selectById(
                new LambdaQueryWrapper<Course>().eq(Course::getId, id)
        );
        if (course == null) {
            log.warn("查询课程不存在，id：{}", id);
            throw new BusinessException("查询课程不存在，id：" + id);
        }
        log.info("查询课程成功，id：{}", id);
        return course;
    }

    @Override
    public void addCourseInfo( Course course) {
        courseMapper.insert(course);
        log.info("添加课程成功,课程名称{},教师名称{}", course.getName(), course.getTeacherName());
    }

    @Override
    public void updateCourseInfo(Long id, Course course) {

        if (course.getStartTime().isAfter(course.getEndTime()) ||
                course.getStartTime().isEqual(course.getEndTime())) {
            log.warn("课程开始时间必须早于结束时间,课程id{}", id);
            throw new BusinessException("课程开始时间必须早于结束时间");
        }

        int rows = courseMapper.update(course,
                new LambdaQueryWrapper<Course>().eq(Course::getId, id));

        if (rows != 1) {
            log.warn("修改课程不存在，id：{}", id);
            throw new BusinessException("修改课程不存在，id：" + id);
        }
        log.info("修改课程成功,课程id{},课程名称{},教师名称{}", id ,course.getName(), course.getTeacherName());
    }

    @Override
    public void delCourseInfo(Long id) {

        int rows = courseMapper.deleteById(id);
        if (rows != 1) {
            log.warn("删除课程不存在，id：{}", id);
            throw new BusinessException("删除课程不存在，id：" + id);
        }
        log.info("删除课程成功,课程id{}", id);
    }

    @Override
    public PageResult<Course> getPageInfo(CourseQueryParam courseQueryParam) {
        // 1. 创建分页对象
        Page<Course> page = new Page<>(courseQueryParam.getPage(),
                courseQueryParam.getPageSize());

        // 2. 创建条件对象
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();

        // 3. 构建查询条件
        buildSearchCondition(courseQueryParam, wrapper);

        // 4. 构建排序条件
        buildSortCondition(wrapper, courseQueryParam.getSortField(),
                courseQueryParam.getSortOrder());


        Page<Course> coursePage = courseMapper.selectPage(page, wrapper);

        log.info("分页查询成功，总记录数：{}，当前页大小：{}",
                coursePage.getTotal(), coursePage.getRecords().size());

        return new PageResult<>(coursePage.getTotal(), coursePage.getRecords());
    }

    // -------------------- 私有辅助方法 --------------------

    private static void buildSearchCondition(CourseQueryParam param,
                                             LambdaQueryWrapper<Course> wrapper) {
        // 课程名称（模糊查询）
        if (param.getName() != null && !param.getName().trim().isEmpty()) {
            wrapper.like(Course::getName, param.getName().trim());
        }

        // 授课教师（模糊查询）
        if (param.getTeacherName() != null && !param.getTeacherName().trim().isEmpty()) {
            wrapper.like(Course::getTeacherName, param.getTeacherName().trim());
        }

        // 学分（精确匹配）
        if (param.getCredit() != null) {
            wrapper.eq(Course::getCredit, param.getCredit());
        }

        // 教室（模糊查询）
        if (param.getClassroom() != null && !param.getClassroom().trim().isEmpty()) {
            wrapper.like(Course::getClassroom, param.getClassroom().trim());
        }

        // 学期（模糊查询）
        if (param.getSemester() != null && !param.getSemester().trim().isEmpty()) {
            wrapper.like(Course::getSemester, param.getSemester().trim());
        }

        // 开放状态（精确匹配，0/1）
        if (param.getIsOpen() != null) {
            wrapper.eq(Course::getIsOpen, param.getIsOpen());
        }
    }

    private void buildSortCondition(LambdaQueryWrapper<Course> wrapper,
                                    String sortField, String sortOrder) {
        boolean isAsc = "asc".equalsIgnoreCase(sortOrder);

        if ("name".equals(sortField)) {
            wrapper.orderBy(true, isAsc, Course::getName);
        } else if ("teacherName".equals(sortField)) {
            wrapper.orderBy(true, isAsc, Course::getTeacherName);
        } else if ("credit".equals(sortField)) {
            wrapper.orderBy(true, isAsc, Course::getCredit);
        } else {
            // 默认按ID倒序
            wrapper.orderByDesc(Course::getId);
        }
    }
}