package com.agriculture;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 农作物疾病监测系统启动类
 */
@SpringBootApplication
@EnableScheduling
@MapperScan({"com.agriculture.dao.mapper", "com.agriculture.modules.*.mapper"})
public class AgricultureApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgricultureApplication.class, args);
        System.out.println("====================================");
        System.out.println("  农作物疾病监测系统启动成功!");
        System.out.println("  接口文档: http://localhost:8080/api/doc.html");
        System.out.println("====================================");
    }
}
