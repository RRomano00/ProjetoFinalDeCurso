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

    // Pool único — mantido como campo para reutilização em getConnection()
    private HikariDataSource hikariDataSource;

    @Bean
    public DataSource dataSource() throws SQLException {
        validateDatabaseName(databaseName);

        // Usa DriverManager diretamente para a conexão de admin (cria banco se não existir).
        // Motivo: DataSourceBuilder cria um segundo HikariPool desnecessário só para isso.
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
                // OWNER usa o próprio usuário conectado (não fixa "postgres"),
                // garantindo a criação em qualquer ambiente com permissão de CREATEDB.
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

    /**
     * Expõe uma Connection do pool para os DAOs.
     * Usa o hikariDataSource já criado — sem criar segundo pool.
     */
    @Bean
    @DependsOn("dataSource")
    public Connection getConnection() throws SQLException {
        if (hikariDataSource == null) {
            throw new IllegalStateException("DataSource ainda não foi inicializado");
        }
        return hikariDataSource.getConnection();
    }

    /**
     * Executa o script de criação/migração de tabelas e o de população de dados.
     * ON CONFLICT DO NOTHING garante idempotência — pode rodar várias vezes.
     */
    @Bean
    @DependsOn("getConnection")
    public boolean createTablesAndInsertData() throws SQLException, IOException {
        final String basePath = "fala-cidade-db-scripts";

        try (Connection connection = hikariDataSource.getConnection()) {

            String createSql = resourceFileService.read(
                basePath + "/PID_SCRIPT_CRIACAO-TABELAS.sql");
            try (PreparedStatement ps = connection.prepareStatement(createSql)) {
                ps.execute();
                log.info("Tabelas verificadas/criadas com sucesso.");
            }

            String insertSql = resourceFileService.read(
                basePath + "/PID_SCRIPT_POPULAR-TABELAS-JWT.sql");
            try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                ps.execute();
                log.info("Dados iniciais inseridos (ON CONFLICT DO NOTHING).");
            }
        }

        return true;
    }
}
