package com.example.demo.aop;

public class LogAspect {
    public void before() {
        System.out.println("方法调用之前进行日志输出");
    }

    public void after() {
        System.out.println("方法调用之后进行日志输出");
    }
}
