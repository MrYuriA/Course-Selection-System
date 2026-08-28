package org.example.util;

public class UserContext {
    private static final ThreadLocal<Long> STUDENT_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Integer> ROLE = new ThreadLocal<>();


    public static void setCurrentStudentId(Long studentId) {
        STUDENT_ID_HOLDER.set(studentId);
    }
    public static Long getCurrentStudentId() {
        return STUDENT_ID_HOLDER.get();
    }

    public static void setRole(Integer role) { ROLE.set(role); }
    public static Integer getRole() { return ROLE.get(); }

    public static void clear() {
        STUDENT_ID_HOLDER.remove();
        ROLE.remove();
    }
}