package br.com.faitec.falacidade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync  // habilita o processamento assíncrono com @Async
public class FalaCidadeApplication {
    public static void main(String[] args) {
        SpringApplication.run(FalaCidadeApplication.class, args);
    }
}
