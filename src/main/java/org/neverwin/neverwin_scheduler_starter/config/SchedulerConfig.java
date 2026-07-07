package org.neverwin.neverwin_scheduler_starter.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.neverwin.neverwin_scheduler_starter.constant.StringConstant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "${neverwin.scheduler.default-lock-at-most-for:PT30S}")
public class SchedulerConfig {

    @Value("${neverwin.scheduler.schema-name:}")
    private String schemaName;

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        String tableName = (schemaName != null && !schemaName.trim().isEmpty())
                ? schemaName + "." + StringConstant.TABLE_SHEDLOCK
                : StringConstant.TABLE_SHEDLOCK;

        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .withTableName(tableName)
                        .usingDbTime()
                        .build()
        );
    }

}