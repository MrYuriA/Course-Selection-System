package org.example.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// @Target指定你自定义的注解 @RequireRole 可以标注在哪些地方。
// ElementType.METHOD 表示这个注解只能用在方法上
// @Retention 用来指定这个注解的生命周期。
// RetentionPolicy.RUNTIME 表示这个注解在运行时仍然保留
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    String value() default "ADMIN"; // 默认要求管理员
}
