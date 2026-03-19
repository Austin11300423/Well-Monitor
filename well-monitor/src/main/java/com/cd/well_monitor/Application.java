package com.cd.well_monitor;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling; // 🌟 1. 导入定时任务包

@SpringBootApplication
@EnableScheduling // 定时扫描引擎！
@MapperScan("com.cd.well_monitor.mapper")
public class Application {

	public static void main(String[] args) {
		// 这行代码是启动整个 Spring Boot 项目
		SpringApplication.run(Application.class, args);

		// 项目启动成功后，会在控制台打印下面的内容
		System.out.println("\n=========================================================");
		System.out.println("后端系统启动成功！");
		System.out.println("🤖 智能预警引擎已挂载，正在后台静默运行(每30秒巡检)..."); // 🌟 加了一句酷炫的提示
		System.out.println("井位台账数据接口测试链接: http://localhost:8080/api/well/list");
		System.out.println("=========================================================\n");
	}



}