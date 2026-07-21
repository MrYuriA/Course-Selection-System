package org.example.util;

public class PageUtil {
    //用工具类来限定和填充常量，顺便封装相关判断方法
    //常量搭配枚举共同使用，解决魔法数字的问题
    public static final int MIN_PAGE_SIZE = 5;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 10;
    //验证页码大小是否超出范围
    public static int validPageSize(Integer size) {
        if (size == null || size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            return DEFAULT_PAGE_SIZE;
        }
        return size;
    }
}