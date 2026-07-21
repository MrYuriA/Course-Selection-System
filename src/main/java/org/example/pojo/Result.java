package org.example.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 后端统一返回体
 */
@Data
@NoArgsConstructor(access = AccessLevel.PRIVATE)  // 不允许外部 new，强制用静态工厂方法
@Schema(description = "统一返回体")
public class Result<T> {

    @Schema(description = "状态码，200成功，其他为错误码")
    private Integer code;

    @Schema(description = "提示信息")
    private String message;                     // 改名为 message，行业惯例

    @Schema(description = "返回数据")
    private T data;                             // 泛型，比 Object 更安全

    // ============ 成功响应 ============

    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.code = 200;                      // HTTP 标准成功码
        result.message = "success";
        return result;
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.code = 200;
        result.message = "success";
        result.data = data;
        return result;
    }

    public static <T> Result<T> success(String message, T data) {
        Result<T> result = new Result<>();
        result.code = 200;
        result.message = message;
        result.data = data;
        return result;
    }

    // ============ 失败响应 ============

    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<>();
        result.code = 400;                      // 默认客户端错误
        result.message = message;
        return result;
    }

    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.code = code;
        result.message = message;
        return result;
    }
}