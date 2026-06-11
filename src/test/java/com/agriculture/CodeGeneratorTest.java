package com.agriculture;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.generator.AutoGenerator;
import com.baomidou.mybatisplus.generator.config.DataSourceConfig;
import com.baomidou.mybatisplus.generator.config.GlobalConfig;
import com.baomidou.mybatisplus.generator.config.PackageConfig;
import com.baomidou.mybatisplus.generator.config.StrategyConfig;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;
import java.util.HashMap;
import java.util.Map;

/**
 * MyBatis-Plus 代码生成器
 * 根据数据库表自动生成 Entity、Mapper、Service、Controller 等代码
 *
 * 使用步骤:
 * 1. 确保 MySQL 已启动且 agri_monitor 数据库中存在目标表
 * 2. 修改下方 TABLES 数组为实际表名
 * 3. 右键运行 generateCode() 方法
 */
public class CodeGeneratorTest {

    /** 数据库连接信息 (与 application.yml 保持一致) */
    private static final String DB_URL = "jdbc:mysql://localhost:3306/agriculture_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false";
    private static final String DB_USER = "agri";
    private static final String DB_PASS = "agri_pwd_2026";

    /** 项目根路径 (绝对路径, 防止生成到错误位置) */
    private static final String PROJECT_PATH = System.getProperty("user.dir");

    /** 包路径 */
    private static final String PARENT_PACKAGE = "com.agriculture";

    /** ========== 在此配置需要生成代码的表名 ========== */
    private static final String[] TABLES = {
            "sys_user",                    // 用户表
            "grid",                        // 大棚网格表
            "camera",                      // 摄像头/设备表
            "camera_grid",                 // 摄像头-网格关联表
            "inference",                   // 推理记录表
            "inspection_plan",             // 巡检计划表
            "inspection_camera",           // 巡检-摄像头关联表
            "inspection_log",              // 巡检日志表
            "work_order",                  // 工单表
            "work_order_history",          // 工单流转历史表
            "audit_record",                // 审核记录表
            "daily_report",                // 日报/简报表
            "ai_conversation",             // AI会话表
            "ai_message",                  // AI消息表
            "prevention_plan",             // 防治方案表
            "prevention_plan_version",     // 防治方案版本表
            "pest_info",                   // 病虫害信息表
            "report",                      // 报告表
            "sys_log"                      // 系统日志表
    };

    /**
     * 运行此方法即可自动生成代码
     * 注意：此方法不会被 Maven 自动执行，需在 IDE 中手动右键运行
     */
    public void generateCode() {
        // 1. 数据源配置
        DataSourceConfig dataSourceConfig = new DataSourceConfig.Builder(DB_URL, DB_USER, DB_PASS)
                .build();

        // 2. 全局配置
        GlobalConfig globalConfig = new GlobalConfig.Builder()
                .author("agriculture-team")
                .outputDir(PROJECT_PATH + "/src/main/java")
                .disableOpenDir()
                .build();

        // 3. 包配置
        PackageConfig packageConfig = new PackageConfig.Builder()
                .parent(PARENT_PACKAGE)
                .entity("entity")
                .mapper("dao.mapper")
                .service("service")
                .serviceImpl("service.impl")
                .controller("controller")
                .build();

        // 4. 策略配置
        StrategyConfig strategyConfig = new StrategyConfig.Builder()
                .addInclude(TABLES)
                // Entity 策略
                .entityBuilder()
                    .idType(IdType.AUTO)
                    .enableLombok()
                    .enableTableFieldAnnotation()
                    .naming(NamingStrategy.underline_to_camel)
                // Mapper 策略
                .mapperBuilder()
                    .enableMapperAnnotation()
                // Service 策略
                .serviceBuilder()
                    .formatServiceFileName("%sService")
                // Controller 策略
                .controllerBuilder()
                    .enableRestStyle()
                    .enableHyphenStyle()
                .build();

        // 5. 执行生成 (模板引擎传入 execute 方法)
        AutoGenerator generator = new AutoGenerator(dataSourceConfig);
        generator.global(globalConfig);
        generator.packageInfo(packageConfig);
        generator.strategy(strategyConfig);
        generator.execute(new FreemarkerTemplateEngine());

        System.out.println("========================================");
        System.out.println("  代码生成完成!");
        System.out.println("  Entity:  " + PROJECT_PATH + "/src/main/java/" + PARENT_PACKAGE.replace(".", "/") + "/entity/");
        System.out.println("  Mapper:  " + PROJECT_PATH + "/src/main/java/" + PARENT_PACKAGE.replace(".", "/") + "/dao/mapper/");
        System.out.println("========================================");
    }
}
