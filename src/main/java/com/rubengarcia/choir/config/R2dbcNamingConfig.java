package com.rubengarcia.choir.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext;

@Configuration
public class R2dbcNamingConfig {

    @Bean
    public R2dbcMappingContext r2dbcMappingContext(R2dbcCustomConversions conversions) {
        R2dbcMappingContext context = new R2dbcMappingContext();
        context.setForceQuote(false);
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        return context;
    }
}
