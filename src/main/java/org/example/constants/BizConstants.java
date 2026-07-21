package org.example.constants;

import java.math.BigDecimal;

public class BizConstants {
    // ========== 学生 ==========
    public static final BigDecimal DEFAULT_MAX_CREDIT = BigDecimal.valueOf(20.0);

    // ========== 课程 ==========
    public static final String CREDIT_MIN = "0.5";
    public static final String CREDIT_MAX = "10.0";

    // ========== JWT ==========
    public static final long TOKEN_EXPIRATION = 1000 * 60 * 60 * 24;
}
