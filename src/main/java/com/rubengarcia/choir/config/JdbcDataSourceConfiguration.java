package com.rubengarcia.choir.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class JdbcDataSourceConfiguration {

    @Bean
    public DataSource dataSource(Environment environment) {
        HikariDataSource dataSource = new HikariDataSource();

        dataSource.setJdbcUrl(
                environment.getRequiredProperty("spring.datasource.url"));
        dataSource.setUsername(
                environment.getProperty("spring.datasource.username", "sa"));
        dataSource.setPassword(
                environment.getProperty("spring.datasource.password", ""));

        String driverClassName =
                environment.getProperty("spring.datasource.driver-class-name");
        if (driverClassName != null && !driverClassName.isBlank()) {
            dataSource.setDriverClassName(driverClassName);
        }

        dataSource.setMaximumPoolSize(
                environment.getProperty(
                        "spring.datasource.hikari.maximum-pool-size",
                        Integer.class,
                        10));

        return dataSource;
    }
}
