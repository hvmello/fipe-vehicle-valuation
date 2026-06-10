package com.fipe.valuation.config;

import com.fipe.valuation.client.CurrentReferenceProvider;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Enables scheduling and registers the periodic FIPE reference-table check. Scheduled
 * programmatically (not via {@code @Scheduled}) so the interval is taken straight from the bound
 * {@link Duration} config, avoiding the string-format limitations of {@code fixedDelayString}.
 *
 * <p>The first resolution happens eagerly at startup (see {@link CurrentReferenceProvider}); this
 * task only handles the recurring re-checks (initial delay = one interval).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer {

    private final CurrentReferenceProvider referenceProvider;
    private final Duration referenceRefresh;

    public SchedulingConfig(CurrentReferenceProvider referenceProvider, FipeProperties properties) {
        this.referenceProvider = referenceProvider;
        this.referenceRefresh = properties.cache().referenceRefresh();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(
                new IntervalTask(referenceProvider::refresh, referenceRefresh, referenceRefresh));
    }
}
