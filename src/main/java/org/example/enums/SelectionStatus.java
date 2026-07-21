package org.example.enums;

//枚举构造器：类似普通类，但是构造方法是private，且能够封装固定状态被外部引用
//枚举与常量的区别在于 枚举指定一或多组互斥的业务状态，常量在于指定固定的单个值
public enum SelectionStatus {
    NORMAL(0, "正常"),
    WITHDRAWN(1, "已退");

    private final int code;
    private final String desc;

    // 这个构造器是私有的，外面不能 new
    SelectionStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }
}
