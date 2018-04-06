package com.demo.domain.controller;

import com.demo.domain.service.DemoService;
import com.demo.mvcframework.annotation.LeeAutowired;
import com.demo.mvcframework.annotation.LeeController;
import com.demo.mvcframework.annotation.LeeRequestMapping;
import com.demo.mvcframework.annotation.LeeRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by Lianhong_ on 2018/04/05 16:42
 */
@LeeController
@LeeRequestMapping("/demo")
public class DemoController {

    @LeeAutowired
    private DemoService demoService;

    @LeeRequestMapping("/query.do")
    public void query(@LeeRequestParam("name") String name, HttpServletRequest request, HttpServletResponse response) {
        String result = demoService.get(name);
        try {
            response.getWriter().print(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
