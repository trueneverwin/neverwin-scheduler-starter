package org.neverwin.neverwin_scheduler_starter.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.jspecify.annotations.NonNull;
import org.neverwin.neverwin_scheduler_starter.annotation.NeverwinScheduler;
import org.neverwin.neverwin_scheduler_starter.properties.SchedulerProperties;
import org.slf4j.MDC;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.FixedRateTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class CustomSchedulerConfig implements SchedulingConfigurer {

    private final ApplicationContext context;
    private final LockProvider lockProvider;
    private final SchedulerProperties schedulerProperties;

    @Override
    public void configureTasks(@NonNull ScheduledTaskRegistrar taskRegistrar) {
        LockingTaskExecutor executor = new DefaultLockingTaskExecutor(lockProvider);

        String[] beanNames = context.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean = context.getBean(beanName);
            Class<?> targetClass = AopUtils.getTargetClass(bean);

            for (Method method : targetClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(NeverwinScheduler.class)) {
                    NeverwinScheduler annotation = method.getAnnotation(NeverwinScheduler.class);
                    String taskKey = annotation.value();
                    SchedulerProperties.Task schedulerTask = schedulerProperties.getTasks().get(taskKey);
                    if (schedulerTask == null) {
                        throw new IllegalArgumentException("Konfigurasi untuk '" + taskKey + "' tidak ditemukan!");
                    }

                    if (schedulerTask.getEnabled() != null && !schedulerTask.getEnabled()) {
                        log.info("Scheduler [{}] dimatikan dari YML. Skipping...", taskKey);
                        continue;
                    }

                    String cron = schedulerTask.getCron();
                    Long fixedRate = schedulerTask.getFixedRate();
                    Long fixedDelay = schedulerTask.getFixedDelay();

                    if (cron == null && fixedRate == null && fixedDelay == null) {
                        throw new IllegalArgumentException("Konfigurasi jadwal (cron/fixed-rate/fixed-delay) untuk '" + taskKey + "' tidak ditemukan!");
                    }

                    adjustDurationLock(schedulerTask);

                    Duration lockAtMostFor = schedulerTask.getLockAtMostFor();
                    Duration lockAtLeastFor = schedulerTask.getLockAtLeastFor();

                    Runnable task = () -> {
                        String correlationId = UUID.randomUUID().toString().replace("-", "");
                        MDC.put("correlationId", correlationId);
                        MDC.put("taskName", taskKey);

                        try {
                            method.setAccessible(true);
                            method.invoke(bean);
                        } catch (Exception e) {
                            throw new RuntimeException("Gagal menjalankan scheduled task: " + taskKey, e);
                        } finally {
                            MDC.remove("correlationId");
                            MDC.remove("taskName");
                        }
                    };

                    Runnable lockedTask = () -> {
                        LockConfiguration lockConfig = new LockConfiguration(
                                Instant.now(),
                                taskKey,
                                lockAtMostFor,
                                lockAtLeastFor
                        );
                        executor.executeWithLock(task, lockConfig);
                    };

                    if (cron != null) {
                        taskRegistrar.addCronTask(lockedTask, cron);
                    } else if (fixedRate != null) {
                        if (schedulerTask.getInitialDelay() != null) {
                            FixedRateTask frTask = new FixedRateTask(lockedTask, Duration.ofMillis(fixedRate), Duration.ofMillis(schedulerTask.getInitialDelay()));
                            taskRegistrar.addFixedRateTask(frTask);
                        } else {
                            taskRegistrar.addFixedRateTask(lockedTask, Duration.ofMillis(fixedRate));
                        }
                    } else {
                        if (schedulerTask.getInitialDelay() != null) {
                            FixedDelayTask fdTask = new FixedDelayTask(lockedTask, Duration.ofMillis(fixedDelay), Duration.ofMillis(schedulerTask.getInitialDelay()));
                            taskRegistrar.addFixedDelayTask(fdTask);
                        } else {
                            taskRegistrar.addFixedDelayTask(lockedTask, Duration.ofMillis(fixedDelay));
                        }
                    }
                }
            }
        }
    }

    private void adjustDurationLock(SchedulerProperties.Task schedulerTask) {
        if (schedulerTask.getLockAtMostFor() == null) schedulerTask.setLockAtMostFor(schedulerProperties.getDefaultLockAtMostFor());
        if (schedulerTask.getLockAtLeastFor() == null) schedulerTask.setLockAtLeastFor(schedulerTask.getLockAtMostFor());
        if (schedulerTask.getLockAtMostFor().compareTo(schedulerTask.getLockAtLeastFor()) < 0) schedulerTask.setLockAtLeastFor(schedulerTask.getLockAtMostFor());
    }
}
