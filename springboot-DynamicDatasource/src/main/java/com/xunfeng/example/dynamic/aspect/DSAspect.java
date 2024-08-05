package com.xunfeng.example.dynamic.aspect;


import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.xunfeng.example.domain.entity.DataSourceEntity;
import com.xunfeng.example.dynamic.DataSourceContextHolder;
import com.xunfeng.example.dynamic.annotation.DataSource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * @author 
 * @date 2024/6/17 15:18
 */
@Aspect
@Component
public class DSAspect {

    @Pointcut("@annotation(com.xunfeng.example.dynamic.annotation.DataSource)")
    public void datasourcePoint() {
    }

    @Around("datasourcePoint()")
    public Object datasourceAround(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        DataSource dataSource = method.getAnnotation(DataSource.class);
        if (Objects.nonNull(dataSource)) {
            // 数据源key
            String key = null;
            // 1.从入参中获取数据源key，并切换
            Object[] args = point.getArgs();
            for (Object arg : args) {
                // 自定义入参标准，这里简单用id作为key
                if (arg instanceof DataSourceEntity) {
                    DataSourceEntity req = (DataSourceEntity) arg;
                    key = req.getId().toString();
                }
            }
            // 2.获取注解中的value为数据源key
            if (StringUtils.isEmpty(key)) {
                key = dataSource.value();
            }
            // 实时切换默认数据源
            DataSourceContextHolder.setDataSource(key);


        }
        try {
            return point.proceed();
        } finally {
            DataSourceContextHolder.removeDataSource();
        }
    }
}
