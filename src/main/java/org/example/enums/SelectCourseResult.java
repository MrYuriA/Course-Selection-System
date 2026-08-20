package org.example.enums;

public enum SelectCourseResult {
    SUCCESS,          // 选课成功
    WAITING,          // 已加入候补
    ALREADY_WAITING,  // 已在候补队列
    FAIL              // 失败（由异常处理）
}
