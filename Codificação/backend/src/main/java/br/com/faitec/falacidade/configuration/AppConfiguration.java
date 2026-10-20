package br.com.faitec.falacidade.configuration;

import br.com.faitec.falacidade.implementation.dao.postgres.DepartmentPostgresDao;
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
import br.com.faitec.falacidade.port.dao.department.DepartmentDao;
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

import javax.sql.DataSource;
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
    public UserDao getUserDao(DataSource dataSource) {
        return new UserPostgresDao(dataSource);
    }

    @Bean
    public OccurrenceDao getOccurrenceDao(DataSource dataSource) {
        return new OccurrencePostgresDao(dataSource);
    }

    @Bean
    public OccurrenceSupportDao getOccurrenceSupportDao(DataSource dataSource) {
        return new OccurrenceSupportPostgresDao(dataSource);
    }

    @Bean
    public PasswordResetTokenDao getPasswordResetTokenDao(DataSource dataSource) {
        return new PasswordResetTokenPostgresDao(dataSource);
    }

    @Bean
    public ContactMessageDao getContactMessageDao(DataSource dataSource) {
        return new ContactMessagePostgresDao(dataSource);
    }

    @Bean
    public DepartmentDao getDepartmentDao(DataSource dataSource) {
        return new DepartmentPostgresDao(dataSource);
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
