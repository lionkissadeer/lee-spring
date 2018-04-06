package com.demo.mvcframework.annotation;

import java.lang.annotation.*;

/**
 * Created by Lianhong_ on 2018/04/05 16:42
 */

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LeeController {
    String value() default "";
}
