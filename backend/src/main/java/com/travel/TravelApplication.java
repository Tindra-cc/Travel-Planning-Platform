package com.travel;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.travel.ai.AiProperties;
import com.travel.ai.DashScopeProperties;
import com.travel.modules.guidebook.data.TravelDataProperties;
import com.travel.modules.guidebook.offline.OfflinePackProperties;

@SpringBootApplication
@MapperScan("com.travel.modules.*.mapper")
@EnableConfigurationProperties({ AiProperties.class, DashScopeProperties.class,
		OfflinePackProperties.class, TravelDataProperties.class })
public class TravelApplication {

	public static void main(String[] args) {
		SpringApplication.run(TravelApplication.class, args);
	}

}
