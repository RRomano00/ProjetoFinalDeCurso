package br.com.faitec.falacidade.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfiguration {

    /**
     * Pool dedicado para uploads do Cloudinary.
     * Nomeado "uploadExecutor" — referenciado em @Async("uploadExecutor").
     *
     * Separado do pool de e-mail para que uploads lentos não bloqueiem
     * o envio de e-mails e vice-versa.
     */
    @Bean(name = "uploadExecutor")
    public Executor uploadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("cloudinary-upload-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Pool dedicado para envio de e-mails.
     * Nomeado "emailExecutor" — referenciado em @Async("emailExecutor").
     *
     * Por que separado?
     *  - SMTP externo (Gmail) pode ter latência variável.
     *  - Mantemos 2 threads sempre prontas para boas-vindas e recuperação
     *    de senha acontecerem ao mesmo tempo sem enfileirar.
     *  - Não atrapalha a fila de uploads do Cloudinary.
     */
    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("email-sender-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        return executor;
    }
}
