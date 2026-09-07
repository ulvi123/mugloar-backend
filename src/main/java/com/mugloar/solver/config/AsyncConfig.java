package com.mugloar.solver.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AsyncConfig implements WebMvcConfigurer {

	@Bean
	public ThreadPoolTaskExecutor autoplayExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(10);
		executor.setMaxPoolSize(20);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("autoplay-");
		executor.initialize();
		return executor;
	}

	@Override
	public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
		configurer.setTaskExecutor(autoplayExecutor());
		configurer.setDefaultTimeout(120_000); //because autoplay can take a long time, intiial testing of mine reported 60 seconds was not enough, so I doubled it to 120 seconds
	}
}