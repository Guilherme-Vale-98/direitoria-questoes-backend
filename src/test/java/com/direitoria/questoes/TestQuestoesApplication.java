package com.direitoria.questoes;

import org.springframework.boot.SpringApplication;

public class TestQuestoesApplication {

	public static void main(String[] args) {
		SpringApplication.from(QuestoesApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
