package com.example.ReportingService.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class SchedularConfig {

    // we need LockProvider bean because without this our @SchedulerLock  does not know how and where to implement the lock.
    //LockProvider is the core interface used by shedLock internally...
    // we are using the JdbcTemplateLockProvider as implementation because we want Locks entries should be stored and managed from Db table
    // Datasource is for making the db connection
    //usingDbTime: is to Uses database server time instead of app server time..
    //as Each instance use its own system clock if we does not use usingDbTime()
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .withLockedByValue("ReportingService_Instance1")
                        .usingDbTime() // recommended
                        .build()
        );
    }
}
