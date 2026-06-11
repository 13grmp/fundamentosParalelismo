package com.service.gabriel.users.config;

import com.service.gabriel.users.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminSeedInitializer implements ApplicationRunner {

	private final UserService userService;
	private final boolean enabled;
	private final String name;
	private final String email;
	private final String password;

	public AdminSeedInitializer(
			UserService userService,
			@Value("${users.admin.seed.enabled:true}") boolean enabled,
			@Value("${users.admin.seed.name:Admin}") String name,
			@Value("${users.admin.seed.email:admin@local}") String email,
			@Value("${users.admin.seed.password:admin123}") String password) {
		this.userService = userService;
		this.enabled = enabled;
		this.name = name;
		this.email = email;
		this.password = password;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (enabled) {
			userService.createAdminIfMissing(name, email, password);
		}
	}
}
