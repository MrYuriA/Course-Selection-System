package org.example.config;

import org.example.filter.JwtAuthFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
//类作为放置描述注册配置过滤器的地方
@Configuration
public class FilterConfig {

    // JwtAuthFilter 是“做什么”，FilterRegistrationBean 是“在哪里做、什么时候做”。
    // FilterRegistrationBean 就是过滤器注册类，用来注册并定义jwtAuthFilter的行为规则
    //方法作为描述注册配置过滤器过程的文本文件
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration(JwtAuthFilter jwtAuthFilter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(jwtAuthFilter);// 设置过滤器
        registration.addUrlPatterns("/api/*"); // 拦截所有 API 接口
        registration.setOrder(1);// 设置过滤器的执行顺序
        return registration;
    }
}