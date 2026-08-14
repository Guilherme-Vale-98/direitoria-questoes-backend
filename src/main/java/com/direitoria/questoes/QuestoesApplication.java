package com.direitoria.questoes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class QuestoesApplication {

	public static void main(String[] args) {
		SpringApplication.run(QuestoesApplication.class, args);
	}

}
