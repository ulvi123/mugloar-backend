package com.mugloar.solver;
import com.mugloar.solver.dto.SolveResponse;
import com.mugloar.solver.service.GameLoopService;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class ScriptRunner {
	public static void main(String[] args) {
		ConfigurableApplicationContext ctx = new SpringApplicationBuilder(MugloarSolverApplication.class)
				.web(WebApplicationType.NONE)
				.run(args);

		GameLoopService loopService = ctx.getBean(GameLoopService.class);
		SolveResponse result = loopService.playNewGame(1000);

		System.out.println("Final score: " + (result != null ? result.score() : 0));
		System.out.println("Final gold: " + (result != null ? result.gold() : 0));
		System.out.println("Lives remaining: " + (result != null ? result.lives() : 0));

		if (result != null && result.score() >= 1000) {
			System.out.println("Target reached.");
		} else {
			System.out.println("Target not reached this run.");
		}
		ctx.close();
	}
}