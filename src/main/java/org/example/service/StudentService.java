package org.example.service;


import org.example.pojo.*;

public interface StudentService {

    Student getStudentInfo(Long id);

    void addStudentInfo(Student student);

    void updateStudentInfo(Long id, Student student);

    void delStudentInfo(Long id);

    PageResult<Student> getPageInfo(StuQueryParam stuQueryParam);

    String login(StudentLoginParam param);

    void register(StudentRegisterParam param);
}
