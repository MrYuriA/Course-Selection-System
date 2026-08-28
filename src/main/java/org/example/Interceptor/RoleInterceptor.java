package org.example.Interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.annotation.RequireRole;
import org.example.exception.BusinessException;
import org.example.util.UserContext;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        //handler 是 Spring MVC 传进来的“处理器”对象，通常就是 Controller 方法，但有时候也可能是其他资源（比如静态资源处理器）。
        //只有 handler 是 HandlerMethod 时，我们才能安全地强转并获取方法上的注解。
        //如果这个请求不是发给 Controller 方法的（比如请求静态资源），就直接放行，不做权限校验。

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            return true; // 没有注解，不限制
        }

        Integer role = UserContext.getRole();
        if (role == null || role != 1) { // 1 代表管理员
            throw new BusinessException(403, "无权限访问");
        }
        return true;
    }
}