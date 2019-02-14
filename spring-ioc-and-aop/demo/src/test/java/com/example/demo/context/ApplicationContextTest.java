package com.example.demo.context;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class ApplicationContextTest {
    @Test
    public void analysis() {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext(new String[]{"spring-config.xml"}, true, null);

        String[] beanDefinitionNames = applicationContext.getBeanDefinitionNames();
        for (String beanDefinitionName : beanDefinitionNames) {
            System.out.println(beanDefinitionName);
        }
//
//        TestService testService = (TestService) applicationContext.getBean("testService");
//        testService.test();

//        HelloService helloService = (HelloService) applicationContext.getBean("helloService");
//        helloService.sayHello();
    }
}
