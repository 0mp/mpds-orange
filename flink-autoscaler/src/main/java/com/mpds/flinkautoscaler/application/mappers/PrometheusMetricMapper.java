package com.mpds.flinkautoscaler.application.mappers;

import com.mpds.flinkautoscaler.domain.model.events.MetricReported;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface PrometheusMetricMapper {

    MetricReported prometheusMetricToMetricReported(ProcessorMetrics processorMetrics);
}
