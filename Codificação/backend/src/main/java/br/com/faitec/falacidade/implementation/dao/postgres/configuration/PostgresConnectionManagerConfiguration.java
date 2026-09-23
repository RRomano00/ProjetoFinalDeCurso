package br.com.faitec.falacidade.implementation.dao.postgres.configuration;

import br.com.faitec.falacidade.port.service.tools.ResourceFileService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.util.logging.Logger;

@Configuration
public class PostgresConnectionManagerConfiguration {

    private static final Logger log =
        Logger.getLogger(PostgresConnectionManagerConfiguration.class.getName());

    private static final String DEFAULT_SEED_PASSWORD = "Admin@1234";

    @Value("${spring.datasource.base.url}")
    private String databaseBaseUrl;

    @Value("${spring.datasource.name}")
    private String databaseName;

    @Value("${spring.datasource.username}")
    private String databaseUsername;

    @Value("${spring.datasource.password}")
    private String databasePassword;

    @Value("${spring.datasource.url}")
    private String databaseUrl;

    @Autowired
    private ResourceFileService resourceFileService;

    private HikariDataSource hikariDataSource;

    @Bean
    public DataSource dataSource() throws SQLException {
        validateDatabaseName(databaseName);

        try (Connection adminConn = DriverManager.getConnection(
                databaseBaseUrl, databaseUsername, databasePassword)) {
            createDatabaseIfNotExists(adminConn);
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(databaseUrl);
        config.setUsername(databaseUsername);
        config.setPassword(databasePassword);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30_000);
        config.setPoolName("FalaCidade-Pool");

        hikariDataSource = new HikariDataSource(config);
        return hikariDataSource;
    }

    private void createDatabaseIfNotExists(Connection connection) throws SQLException {
        String sql =
            "SELECT COUNT(*) AS dbs FROM pg_catalog.pg_database " +
            "WHERE lower(datname) = lower('" + databaseName + "')";

        try (Statement stmt = connection.createStatement();
             ResultSet rs   = stmt.executeQuery(sql)) {

            if (rs.next() && rs.getInt("dbs") == 0) {
                stmt.executeUpdate(
                    "CREATE DATABASE " + databaseName +
                    " WITH OWNER = " + databaseUsername +
                    " ENCODING = 'UTF8' CONNECTION LIMIT = -1"
                );
                log.info("Banco de dados criado: " + databaseName);
            } else {
                log.info("Banco de dados já existe: " + databaseName);
            }
        }
    }

    private void validateDatabaseName(String name) {
        if (!name.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Nome do banco inválido: " + name);
        }
    }

    @Bean
    @DependsOn("dataSource")
    public Connection getConnection() throws SQLException {
        if (hikariDataSource == null) {
            throw new IllegalStateException("DataSource ainda não foi inicializado");
        }
        return hikariDataSource.getConnection();
    }

    @Bean
    @DependsOn("getConnection")
    public boolean createTablesAndInsertData() throws SQLException, IOException {
        final String basePath = "fala-cidade-db-scripts";

        try (Connection connection = hikariDataSource.getConnection()) {

            String createSql = resourceFileService.read(
                basePath + "/FalaCidade_DDL_CriacaoTabelas.sql");
            try (PreparedStatement ps = connection.prepareStatement(createSql)) {
                ps.execute();
                log.info("Tabelas verificadas/criadas com sucesso.");
            }

            String insertSql = resolveSeedPassword(resourceFileService.read(
                basePath + "/FalaCidade_DML_PopulacaoTabelas.sql"));
            try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                ps.execute();
                log.info("Dados iniciais inseridos (ON CONFLICT DO NOTHING).");
            }
        }

        return true;
    }

    private String resolveSeedPassword(String sql) {
        String password = System.getenv("FALACIDADE_SEED_PASSWORD");
        if (password == null || password.isBlank()) {
            password = DEFAULT_SEED_PASSWORD;
            log.info("FALACIDADE_SEED_PASSWORD não definida. Contas de demonstração usam "
                + "a senha padrão " + DEFAULT_SEED_PASSWORD + " — altere-a no primeiro acesso.");
        }
        if (password.contains("'")) {
            throw new IllegalArgumentException("FALACIDADE_SEED_PASSWORD não pode conter apóstrofo");
        }
        return sql.replace("${FALACIDADE_SEED_PASSWORD}", password);
    }
}
