package org.example.exception;


import org.example.pojo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 兜底异常处理：未知的系统错误，需要立即排查
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统异常，需立即排查", e);
        return Result.error(500, "系统内部错误");
    }

    /**
     * 代码 bug：空指针、数组越界等，需要立即修复
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<?> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常，可能是代码 bug", e);
        return Result.error(500, "系统内部错误");
    }

    /**
     * 数据库唯一键冲突：用户重复操作或并发插入
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<?> handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("数据重复冲突：{}", e.getMessage());
        String message = e.getMessage();
        int i = message.indexOf("Duplicate entry");
        String errMsg = message.substring(i);
        String[] s = errMsg.split(" ");
        return Result.error(400, s[2] + "已存在");
    }

    /**
     * 参数校验失败：前端传了非法参数
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldError().getDefaultMessage();
        log.warn("参数校验失败：{}", message);
        return Result.error(400, message);
    }

    /**
     * 业务异常：用户操作不当或业务规则限制
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常：{}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }
}
