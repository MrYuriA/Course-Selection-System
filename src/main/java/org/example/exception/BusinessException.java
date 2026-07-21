package org.example.exception;

public class BusinessException extends RuntimeException{

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(400, message);  // 默认业务错误码
    }

    public int getCode() { return code; }

}
