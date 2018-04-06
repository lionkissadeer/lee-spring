package com.demo.mvcframework.servlet;

import com.demo.mvcframework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Lianhong_ on 2018/04/05 16:31
 */
public class LeeDispatcherServlet extends HttpServlet {

    private Properties properties = new Properties();

    private List<String> classNames = new ArrayList<String>();

    private Map<String, Object> iocMap = new HashMap<String, Object>();

//    private Map<String, Method> handlingMapping = new HashMap<String, Method>();

    private List<Handler> handlerMapping = new ArrayList<Handler>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        //1 加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        //2 扫描所有的相关类
        doScanner(properties.getProperty("scanPackage"));

        //3 初始化所有相关class的实例，并保存到IOC容器中
        doInstance();

        //4 自动化依赖注入
        doAutowired();

        //5 初始化HandlerMapping
        initHandlerMapping();

        System.out.println("mvc framework is inited.");
    }

    protected void doGet(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException, IOException {
        doPost(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doDispatch(request, response);
    }


    private void doLoadConfig(String location) {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(location);
        try {
            properties.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (null != is) {
                    is.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void doScanner(String packageName) {
        URL url = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));
        File classDir = new File(url.getFile());
        for (File file : classDir.listFiles()) {
            if (file.isDirectory()) {
                doScanner(packageName + "." + file.getName());
            } else
                classNames.add(packageName + "." + file.getName().replace(".class", ""));
        }
    }

    private void doInstance() {
        if (classNames.isEmpty()) return;
        try {
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                //进行实例化，只实例化注解@LeeController/@LeeService的类
                if (clazz.isAnnotationPresent(LeeController.class)) {
                    String beanName = lowerFirst(clazz.getSimpleName());
                    iocMap.put(beanName, clazz.newInstance());
                } else if (clazz.isAnnotationPresent(LeeService.class)) {

                    //1 默认采用类型的首字母小写
                    //2 优先使用自定义value
                    //3 根据类型匹配，利用接口作为key
                    LeeService service = clazz.getAnnotation(LeeService.class);
                    String beanName = service.value();
                    if ("".equals(beanName.trim())) { //1
                        beanName = lowerFirst(clazz.getSimpleName());
                    }

                    Object instance = clazz.newInstance(); //2
                    iocMap.put(beanName, instance);

                    Class<?>[] interfaces = clazz.getInterfaces(); //3
                    for (Class<?> i : interfaces) {
                        iocMap.put(i.getName(), instance);
                    }
                } else continue;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void doAutowired() {
        if (iocMap.isEmpty()) return;
        for (Map.Entry<String, Object> entry : iocMap.entrySet()) {
            //getDeclaredFields() 取出所有fields，包括private
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                //只取加了 @LeeAutowired 注解的field
                if (!field.isAnnotationPresent(LeeAutowired.class)) continue;
                LeeAutowired autowired = field.getAnnotation(LeeAutowired.class);
                String beanName = autowired.value().trim();
                if ("".equals(beanName)) {
                    beanName = field.getType().getName();
                }

                //暴力访问
                field.setAccessible(true);

                try {
                    field.set(entry.getValue(), iocMap.get(beanName));
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }

    private void initHandlerMapping() {
        if (iocMap.isEmpty()) return;

        for (Map.Entry<String, Object> entry : iocMap.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            //HandlerMapping 只认识 @LeeController
            if (!clazz.isAnnotationPresent(LeeController.class)) continue;
            String uri = "";
            if (clazz.isAnnotationPresent(LeeRequestMapping.class)) {
                LeeRequestMapping requestMapping = clazz.getAnnotation(LeeRequestMapping.class);
                uri = requestMapping.value();
            }
            Method[] methods = clazz.getMethods();

           /* for (Method method : methods) {
                if (!method.isAnnotationPresent(LeeRequestMapping.class)) continue;
                LeeRequestMapping requestMapping = method.getAnnotation(LeeRequestMapping.class);
                String muri = (uri + requestMapping.value());
                handlingMapping.put(muri, method);
                System.out.println("Mapping : " + muri + "   " + method);
            }*/

            for (Method method : methods) {
                if (!method.isAnnotationPresent(LeeRequestMapping.class)) continue;
                LeeRequestMapping requestMapping = method.getAnnotation(LeeRequestMapping.class);

                String reg = ("/" + uri + requestMapping.value()).replaceAll("/+", "/");
                Pattern pattern = Pattern.compile(reg);
                handlerMapping.add(new Handler(entry.getValue(), method, pattern));
                System.out.println("mapping " + reg + "," + method);
            }
        }
    }

    private void doDispatch(HttpServletRequest request, HttpServletResponse response) throws IOException {
        /*String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        uri = uri.replace(contextPath, "").replaceAll("/+", "/");
        if (!handlingMapping.containsKey(uri)) {
            response.getWriter().print("404 Not Found!");
            return;
        }
        Method method = handlingMapping.get(uri);
        Object obj = method.invoke();
        System.out.println("对应的方法是：" + method);*/
        try {

            Handler handler = getHandler(request);

            if (handler == null) {
                response.getWriter().print("404 Not Found!");
                return;
            }
            //获取方法的参数列表
            Class<?>[] paramsTypes = handler.method.getParameterTypes();

            //保存所有需要自动赋值的参数值
            Object[] paramsValues = new Object[paramsTypes.length];

            Map<String, String[]> params = request.getParameterMap();
            for (Map.Entry<String, String[]> param : params.entrySet()) {
                String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");

                //如果找到匹配的对象，则开始填充参数值
                if (!handler.paramIndexMapping.containsKey(param.getKey())) continue;
                int index = handler.paramIndexMapping.get(param.getKey());
                paramsValues[index] = convert(paramsTypes[index], value);
            }

            //设置方法中的request和response对象
            int requestIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramsValues[requestIndex] = request;

            int responseIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramsValues[responseIndex] = response;

            handler.method.invoke(handler.controller, paramsValues);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private Handler getHandler(HttpServletRequest request) {
        if (handlerMapping.isEmpty()) return null;

        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        uri = uri.replace(contextPath, "").replaceAll("/+", "/");

        for (Handler handler : handlerMapping) {
            Matcher matcher = handler.pattern.matcher(uri);
            if (!matcher.matches()) continue;
            return handler;
        }
        return null;
    }

    private Object convert(Class<?> type, String value) {
        return Integer.class == type ? Integer.valueOf(value) : value;
    }

    private String lowerFirst(String str) {
        char[] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    /**
     * Handler 记录 Controller 中的 RequestMapping 和 Method 的对应关系
     */
    private class Handler {

        protected Object controller; //保存方法对应的实例
        protected Method method;     //保存映射的方法
        protected Pattern pattern;
        protected Map<String, Integer> paramIndexMapping; //参数序列

        protected Handler(Object controller, Method method, Pattern pattern) {
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;

            this.paramIndexMapping = new HashMap<String, Integer>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method) {
            Annotation[][] pa = method.getParameterAnnotations();
            for (int i = 0; i < pa.length; i++) {
                for (Annotation a : pa[i]) {
                    String paramName = ((LeeRequestParam) a).value();
                    if (!"".equals(paramName.trim())) {
                        paramIndexMapping.put(paramName, i);
                    }
                }
            }

            Class<?>[] paramTypes = method.getParameterTypes();
            for (int i = 0; i < paramTypes.length; i++) {
                Class<?> type = paramTypes[i];
                if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
                    paramIndexMapping.put(type.getName(), i);
                }
            }
        }
    }
}
