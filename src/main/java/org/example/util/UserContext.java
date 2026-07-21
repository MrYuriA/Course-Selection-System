package org.example.util;

public class UserContext {
    private static final ThreadLocal<Long> STUDENT_ID_HOLDER = new ThreadLocal<>();

    public static void setCurrentStudentId(Long studentId) {
        STUDENT_ID_HOLDER.set(studentId);
    }

    public static Long getCurrentStudentId() {
        return STUDENT_ID_HOLDER.get();
    }

    public static void clear() {
        STUDENT_ID_HOLDER.remove();
    }
}