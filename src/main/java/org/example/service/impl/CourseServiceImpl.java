package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.cache.CacheService;
import org.example.exception.BusinessException;
import org.example.mapper.CourseMapper;
import org.example.pojo.Course;
import org.example.pojo.CourseQueryParam;
import org.example.pojo.PageResult;
import org.example.service.CourseService;
import org.example.util.CacheKeyUtil;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private  final CourseMapper courseMapper;
    private final CacheService cacheService;

    // ---------- 缓存 Key 前缀定义 ----------
    private static final String CACHE_DETAIL_PREFIX = "course:detail:";      // 单条课程详情
    private static final String CACHE_PAGE_PREFIX   = "course:page:";        // 分页列表（后面拼接参数签名）

    @Override //查看课程详情拉进缓存
    public Course getCourseInfo(Long id) {
        // 参数校验
        if (id == null || id <= 0) {
            log.warn("查询课程传入无效 id: {}", id);
            throw new BusinessException("无效的课程ID");
        }

        String key = CACHE_DETAIL_PREFIX + id;

        // 调用手动缓存：防穿透 + 防雪崩
        Course course = cacheService.getOrLoad(
                key,
                Course.class,
                () -> courseMapper.selectById(id),   // 数据库查询逻辑
                Duration.ofMinutes(10),              // 真实数据缓存10分钟（内部会自动加随机）
                Duration.ofSeconds(30)               // 空值标记缓存30秒
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
        // 新增课程会影响分页列表，清除所有分页缓存（模拟 allEntries = true）
        cacheService.evictPattern(CACHE_PAGE_PREFIX + "*");
        // 注意：新增课程不会产生旧详情缓存，所以不需要删除详情 Key
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
        // ----- 手动清除缓存（替代 @CacheEvict） -----
        // ① 删除该课程的详情缓存
        cacheService.evict(CACHE_DETAIL_PREFIX + id);
        // ② 删除所有分页列表缓存（因为数据变了，所有分页结果都可能受影响）
        cacheService.evictPattern(CACHE_PAGE_PREFIX + "*");
    }

    @Override
    public void delCourseInfo(Long id) {

        int rows = courseMapper.deleteById(id);
        if (rows != 1) {
            log.warn("删除课程不存在，id：{}", id);
            throw new BusinessException("删除课程不存在，id：" + id);
        }
        log.info("删除课程成功,课程id{}", id);
        // ----- 手动清除缓存（替代 @CacheEvict） -----
        cacheService.evict(CACHE_DETAIL_PREFIX + id);
        cacheService.evictPattern(CACHE_PAGE_PREFIX + "*");
    }

    @Override //查询大量课程信息拉近缓存
    public PageResult<Course> getPageInfo(CourseQueryParam courseQueryParam) {
        // 生成缓存 Key：利用参数对象生成唯一签名
        String key = CacheKeyUtil.generate(CACHE_PAGE_PREFIX, courseQueryParam);
        // 手动缓存：分页数据也适用“防穿透”，但空值标记不适用于集合，这里直接返回 null 时正常逻辑会抛异常
        // 我们使用 getOrLoad 但泛型为 PageResult，空值标记存为 "NULL" 不影响（反正 PageResult 不会为 null）
        PageResult<Course> pageResult = cacheService.getOrLoad(
                key,
                PageResult.class,
                () -> {
                    // 构建分页参数
                    Page<Course> page = new Page<>(courseQueryParam.getPage(),
                            courseQueryParam.getPageSize());
                    LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();

                    buildSearchCondition(courseQueryParam, wrapper);
                    buildSortCondition(wrapper, courseQueryParam.getSortField(),
                            courseQueryParam.getSortOrder());

                    Page<Course> coursePage = courseMapper.selectPage(page, wrapper);
                    log.info("分页查询成功，总记录数：{}，当前页大小：{}",
                            coursePage.getTotal(), coursePage.getRecords().size());
                    return new PageResult<>(coursePage.getTotal(), coursePage.getRecords());
                },
                Duration.ofMinutes(10),   // 分页缓存 10 分钟
                Duration.ofSeconds(30)    // 空值标记 30 秒（虽然 PageResult 不会为 null，但保留以防）
        );

        // 如果数据库查询确实返回了空列表，PageResult 不会为 null，所以不需要额外判断
        return pageResult;
    }

    // -------------------- 私有辅助方法 --------------------

    private static void buildSearchCondition(CourseQueryParam param,
                                             LambdaQueryWrapper<Course> wrapper) {
        // 课程ID（精确匹配）
        if (param.getId() != null) {
            wrapper.eq(Course::getId, param.getId());
        }
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

        // 1. 确定排序字段（如果为空，默认使用 id）
        String finalSortField = (sortField == null || sortField.isBlank()) ? "id" : sortField;

        // 2. 确定排序方向（如果为空或非法，默认降序 desc）
        //    只要 sortOrder 是 "asc"（不区分大小写）就升序，其余情况（包括空、null、desc）都降序
        boolean isAsc = "asc".equalsIgnoreCase(sortOrder);

        // 3. 根据确定的字段和方向构建排序条件
        if ("name".equals(finalSortField)) {
            wrapper.orderBy(true, isAsc, Course::getName);
        } else if ("teacherName".equals(finalSortField)) {
            wrapper.orderBy(true, isAsc, Course::getTeacherName);
        } else if ("credit".equals(finalSortField)) {
            wrapper.orderBy(true, isAsc, Course::getCredit);
        } else {
            // 默认按 ID 排序（包括 finalSortField 为 "id" 或未知字段的情况）
            wrapper.orderBy(true, isAsc, Course::getId);
        }
    }
}