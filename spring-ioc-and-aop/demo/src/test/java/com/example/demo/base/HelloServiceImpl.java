package com.example.demo.base;

import org.springframework.beans.factory.annotation.Required;

public class HelloServiceImpl implements HelloService {
    private String content;
    private OutputService outputService;

    @Override
    public void sayHello() {
        outputService.print(content);
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Required
    public void setOutputService(OutputService outputService) {
        this.outputService = outputService;
    }
}
