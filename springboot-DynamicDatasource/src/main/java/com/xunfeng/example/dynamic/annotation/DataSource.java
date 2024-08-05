package com.xunfeng.example.dynamic.annotation;

import java.lang.annotation.*;

/**
 * @author 
 * @date 2024/6/17 15:17
 */
@Target({ElementType.METHOD,ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface DataSource {
    String value() default "master";
}
