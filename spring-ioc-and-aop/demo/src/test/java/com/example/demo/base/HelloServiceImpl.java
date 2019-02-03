package com.example.demo.base;

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

    public void setOutputService(OutputService outputService) {
        this.outputService = outputService;
    }
}
