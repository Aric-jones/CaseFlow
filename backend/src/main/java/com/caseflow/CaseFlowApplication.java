package com.caseflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.caseflow.mapper")
public class CaseFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(CaseFlowApplication.class, args);
    }
}
