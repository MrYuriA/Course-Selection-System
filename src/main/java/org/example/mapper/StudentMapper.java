package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.example.pojo.StuQueryParam;
import org.example.pojo.Student;

import java.util.List;

@Mapper
public interface StudentMapper extends BaseMapper<Student> {

}
