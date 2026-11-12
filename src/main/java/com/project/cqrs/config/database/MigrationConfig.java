package com.project.cqrs.config.database;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import javax.sql.DataSource;
@Configuration
public class MigrationConfig {
    @Bean(initMethod = "migrate")
    public Flyway commandFlyway(@Qualifier("commandDataSource") DataSource source) {
        return Flyway.configure().dataSource(source).locations("classpath:db/migration/command").load();
    }
    @Bean(initMethod = "migrate")
    public Flyway queryFlyway(@Qualifier("queryDataSource") DataSource source) {
        return Flyway.configure().dataSource(source).locations("classpath:db/migration/query").load();
    }
}