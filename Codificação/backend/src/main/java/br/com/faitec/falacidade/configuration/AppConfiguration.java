package br.com.faitec.falacidade.configuration;

import br.com.faitec.falacidade.implementation.dao.postgres.ContactMessagePostgresDao;
import br.com.faitec.falacidade.implementation.dao.postgres.OccurrencePostgresDao;
import br.com.faitec.falacidade.implementation.dao.postgres.OccurrenceSupportPostgresDao;
import br.com.faitec.falacidade.implementation.dao.postgres.PasswordResetTokenPostgresDao;
import br.com.faitec.falacidade.implementation.dao.postgres.UserPostgresDao;
import br.com.faitec.falacidade.implementation.service.authentication.JwtAuthenticationServiceImpl;
import br.com.faitec.falacidade.port.dao.contact.ContactMessageDao;
import br.com.faitec.falacidade.port.dao.occurrence.OccurrenceDao;
import br.com.faitec.falacidade.port.dao.occurrence.OccurrenceSupportDao;
import br.com.faitec.falacidade.port.dao.password.PasswordResetTokenDao;
import br.com.faitec.falacidade.port.dao.user.UserDao;
import br.com.faitec.falacidade.port.service.authentication.AuthenticationService;
import br.com.faitec.falacidade.port.service.user.UserService;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.Connection;
import java.util.Arrays;
import java.util.logging.Logger;

@Configuration
public class AppConfiguration {

    private static final Logger log = Logger.getLogger(AppConfiguration.class.getName());

    private final Environment environment;

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    @Value("${cloudinary.api-key}")
    private String apiKey;

    @Value("${cloudinary.api-secret}")
    private String apiSecret;

    public AppConfiguration(Environment environment) {
        this.environment = environment;
        log.info("Active profiles: " + Arrays.toString(environment.getActiveProfiles()));
    }

    @Bean
    public UserDao getUserDao(Connection connection) {
        return new UserPostgresDao(connection);
    }

    @Bean
    public OccurrenceDao getOccurrenceDao(Connection connection) {
        return new OccurrencePostgresDao(connection);
    }

    @Bean
    public OccurrenceSupportDao getOccurrenceSupportDao(Connection connection) {
        return new OccurrenceSupportPostgresDao(connection);
    }

    @Bean
    public PasswordResetTokenDao getPasswordResetTokenDao(Connection connection) {
        return new PasswordResetTokenPostgresDao(connection);
    }

    @Bean
    public ContactMessageDao getContactMessageDao(Connection connection) {
        return new ContactMessagePostgresDao(connection);
    }

    @Bean
    public Cloudinary cloudinary() {
        return new Cloudinary(ObjectUtils.asMap(
            "cloud_name", cloudName,
            "api_key",    apiKey,
            "api_secret", apiSecret,
            "secure",     true
        ));
    }

    @Bean
    @Profile("jwt")
    public AuthenticationService jwtAuthenticationService(UserService userService,
                                                           PasswordEncoder passwordEncoder) {
        return new JwtAuthenticationServiceImpl(userService, passwordEncoder);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public OpenAPI customOpenApi() {
        return new OpenAPI().info(new Info()
            .title("Fala, Cidade! – API")
            .version("0.0.1")
            .description("API REST do sistema Fala, Cidade! – PFC FAI 2026"));
    }
}
