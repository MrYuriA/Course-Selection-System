package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.constants.BizConstants;
import org.example.exception.BusinessException;
import org.example.mapper.StudentMapper;
import org.example.pojo.*;
import org.example.service.StudentService;
import org.example.util.JwtUtil;
import org.example.util.PageUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private final StudentMapper studentMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtUtil jwtUtil;


    //登录
    @Override
    public String login(StudentLoginParam param) {
        Student student = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, param.getStudentNo()));
        if (student == null) {
            log.warn("登录失败：学生不存在，学号：{}", param.getStudentNo());
            throw new BusinessException("学号或密码错误");
        }
        if (!passwordEncoder.matches(param.getPassword(), student.getPassword())) {
            log.warn("登录失败：密码错误，学号：{}", param.getStudentNo());
            throw new BusinessException("学号或密码错误");
        }
        log.info("前端传入密码：{}", param.getPassword());
        log.info("数据库存储密码：{}", student.getPassword());
        String token = jwtUtil.generateToken(student.getId(),student.getRole() );
        log.info("登录成功，学号：{}，学生ID：{}", param.getStudentNo(), student.getId());
        return token;
    }

    //注册
    @Override
    public void register(StudentRegisterParam param) {
        //按照学号查询，查询是否有同学号且没被逻辑删除的学生，如果没有则可以正常注册，否则返回学号已存在业务异常给全局异常处理器
        Student student = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, param.getStudentNo())
                .eq(Student::getDeleted, 0)
        );
        if (student != null) {
            log.warn("注册失败：学生已存在，学号：{}", param.getStudentNo());
            throw new BusinessException("学号已存在");
        }
        student = Student.builder()
                .name(param.getName())
                .studentNo(param.getStudentNo())
                .maxCredit(BizConstants.DEFAULT_MAX_CREDIT)
                .password(passwordEncoder.encode(param.getPassword()))
                .build();
        studentMapper.insert(student);
        log.info("注册成功，学号：{}，学生ID：{}", param.getStudentNo(), student.getId());
    }

    //按照id查询,查看个人信息拉进缓存
    @Override
    public Student getStudentInfo(Long id) {

        Student student = studentMapper.selectById(new LambdaQueryWrapper<Student>().eq(Student::getId, id));
        if (student == null) {
            log.warn("查询失败：学生不存在。学生ID: {}", id);
            throw new BusinessException("查询学生不存在，id：" + id);   // 抛异常，让上层处理
        }
        log.info("查询成功。学生ID: {}, 姓名: {}, 学号: {}, 最高学分: {}",
                student.getId(), student.getName(), student.getStudentNo(), student.getMaxCredit());
        return student;
    }

    //新增学生
    @Override
    public void addStudentInfo(Student student) {
        studentMapper.insert(student);
        log.info("添加成功。 姓名: {}, 学号: {}, 最高学分: {}",
                 student.getName(), student.getStudentNo(), student.getMaxCredit());
    }

    //修改学生
    @Override
    public void updateStudentInfo(Long id, Student student) {

        int rows = studentMapper.update(student, new LambdaQueryWrapper<Student>().eq(Student::getId, id));
        if (rows != 1) {
            log.warn("修改失败：学生不存在。学生ID: {}", id);
            throw new BusinessException("修改学生不存在，id：" + id);
        }
        log.info("修改成功。 姓名: {}, 学号: {}, 最高学分: {}",
                 student.getName(), student.getStudentNo(), student.getMaxCredit());
    }

    //删除学生
    @Override
    public void delStudentInfo(Long id) {

        int rows = studentMapper.deleteById(id);
        if (rows != 1) {
            log.warn("删除失败：学生不存在。学生ID: {}", id);
            throw new BusinessException("删除学生不存在，id：" + id);
        }
        log.info("删除成功。学生ID: {}", id);
    }

    //条件分页查询
    @Override
    public PageResult<Student> getPageInfo(StuQueryParam stuQueryParam) {

        // 关键：对 pageSize 做范围限制
        int safePageSize = PageUtil.validPageSize(stuQueryParam.getPageSize());
        stuQueryParam.setPageSize(safePageSize);

        Page<Student> page = new Page<>(stuQueryParam.getPage(), stuQueryParam.getPageSize());      // 1. 创建分页对象

        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();  // 2. 创建条件对象

        buildSearchCondition(stuQueryParam, wrapper); // 3. 构建查询条件

        buildSortCondition(wrapper, stuQueryParam.getSortField(), stuQueryParam.getSortOrder()); // 4. 构建排序条件


        Page<Student> studentPage = studentMapper.selectPage(page, wrapper);

        log.info("分页查询成功，总记录数: {}, 当前页大小: {}",
                studentPage.getTotal(), studentPage.getRecords().size());

        return new PageResult<>(studentPage.getTotal(), studentPage.getRecords());
    }

    private static void buildSearchCondition(StuQueryParam stuQueryParam, LambdaQueryWrapper<Student> wrapper) {
        // 只有当 name 有实际内容时才拼接
        if (stuQueryParam.getName() != null && !stuQueryParam.getName().trim().isEmpty()) {
            wrapper.like(Student::getName, stuQueryParam.getName());
        }

        // 只有当 credit 有实际内容时才拼接
        if (stuQueryParam.getMaxCredit() != null) {
            wrapper.eq(Student::getMaxCredit, stuQueryParam.getMaxCredit());
        }

        // 只有当 studentNo 有实际内容时才拼接
        if (stuQueryParam.getStudentNo() != null && !stuQueryParam.getStudentNo().trim().isEmpty()) {
            // 注意：这里如果想和上面的 name 做 OR，要特殊处理
            // 简单起见，先用 AND 逻辑（同时匹配姓名和学号）
            String trimmedNo = stuQueryParam.getStudentNo().trim();
            if (trimmedNo.length() >= 8) {  // 假设学号8位
                wrapper.eq(Student::getStudentNo, trimmedNo);
            } else {
                wrapper.like(Student::getStudentNo, trimmedNo);
            }
        }
    }

    private void buildSortCondition(LambdaQueryWrapper<Student> wrapper, String sortField, String sortOrder) {

        boolean isAsc = "asc".equalsIgnoreCase(sortOrder);

        if ("name".equals(sortField)) {
            wrapper.orderBy(true, isAsc, Student::getName);
        } else if ("studentNo".equals(sortField)) {
            wrapper.orderBy(true, isAsc, Student::getStudentNo);
        } else {
            wrapper.orderByDesc(Student::getId);  // 默认按ID倒序
        }
    }

}
