package com.demo.domain.service.impl;

import com.demo.domain.service.DemoService;
import com.demo.mvcframework.annotation.LeeService;

/**
 * Created by Lianhong_ on 2018/04/05 16:40
 */
@LeeService
public class DefaultDemoService implements DemoService {

    public String get(String name) {
        return "Hello, My name is " + name;
    }
}
