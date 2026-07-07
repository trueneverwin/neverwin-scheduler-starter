package org.neverwin.neverwin_scheduler_starter.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "neverwin.scheduler")
public class SchedulerProperties {
    private String schemaName;
    private Duration defaultLockAtMostFor = Duration.of(30, ChronoUnit.SECONDS);
    private Map<String, Task> tasks;

    @Data
    public static class Task {
        private Boolean enabled;
        private Long initialDelay = 0L;
        private String cron;
        private Long fixedRate;
        private Long fixedDelay;
        private Duration lockAtMostFor;
        private Duration lockAtLeastFor = Duration.of(20, ChronoUnit.SECONDS);
    }

}
