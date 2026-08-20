package org.example.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.util.JwtUtil;
import org.example.util.UserContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
//Jwt过滤器，定义当请求到达时，检查请求路径，根据路径选择拦截或者放行，如果拦截则检查请求头中是否包含Token，并校验Token的合法性。
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter { // 也可以实现 Filter 接口，用 OncePerRequestFilter 更简单

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        // 登录和注册接口直接放行
        if (path.contains("/api/students/login")|| path.contains("/api/students/register")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 拿取并校验 Token 校验：是否符合JWT规范
        String token = request.getHeader("Authorization");
        if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
            token = token.substring(7);
            //首先，校验token是否合规合法
            //然后，拿取学生ID字段存入线程内存
            if (jwtUtil.validateToken(token)) {
                Long studentId = jwtUtil.getStudentIdFromToken(token);
                UserContext.setCurrentStudentId(studentId);
                try {
                    filterChain.doFilter(request, response);
                } finally {//如果放行后报错 线程清理可能不会执行，使用finally保证线程内存清理
                    UserContext.clear();
                } // 请求结束清理 ThreadLocal

                //以下两个else分支，用于处理token无效或已过期的情况
            } else {
                response.setContentType("application/json;charset=UTF-8");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"code\":401,\"message\":\"Token无效或已过期\"}");
            }
        } else {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":401,\"message\":\"未提供Token\"}");
        }
    }
}