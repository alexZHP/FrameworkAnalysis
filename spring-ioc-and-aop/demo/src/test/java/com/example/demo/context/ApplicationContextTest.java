package com.example.demo.context;

import com.example.demo.base.HelloService;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class ApplicationContextTest {
    @Test
    public void analysis() {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext(new String[] {"spring-config.xml"}, true, null);
        HelloService helloService = (HelloService) applicationContext.getBean("helloService");
        helloService.sayHello();
    }
}
