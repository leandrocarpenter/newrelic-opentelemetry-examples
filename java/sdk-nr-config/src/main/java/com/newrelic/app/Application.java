package com.newrelic.app;

import com.newrelic.shared.OpenTelemetryConfig;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.instrumentation.log4j.appender.v2_16.OpenTelemetryAppender;
import io.opentelemetry.instrumentation.runtimemetrics.GarbageCollector;
import io.opentelemetry.instrumentation.spring.webmvc.SpringWebMvcTracing;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.servlet.Filter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {

  public static void main(String[] args) {
    // Configure OpenTelemetry as early as possible
    var defaultServiceName = "sdk-nr-config";
    var openTelemetrySdk = OpenTelemetryConfig.configureGlobal(defaultServiceName);

    // Initialize Log4j2 appender
    OpenTelemetryAppender.setSdkLogEmitterProvider(openTelemetrySdk.getSdkLogEmitterProvider());

    GarbageCollector.registerObservers();
    registerMemoryObservers(openTelemetrySdk);

    SpringApplication.run(Application.class, args);
  }

  /** Add Spring WebMVC instrumentation by registering tracing filter. */
  @Bean
  public Filter webMvcTracingFilter() {
    return SpringWebMvcTracing.create(GlobalOpenTelemetry.get()).newServletFilter();
  }

  public static void registerMemoryObservers(OpenTelemetry openTelemetry) {
    List<MemoryPoolMXBean> poolBeans = ManagementFactory.getMemoryPoolMXBeans();
    Meter meter = openTelemetry.getMeter("io.opentelemetry.runtime-metrics");

    meter
        .upDownCounterBuilder("process.runtime.jvm.memory.usage")
        .setDescription("Measure of memory used")
        .setUnit("By")
        .buildWithCallback(callback(poolBeans, MemoryUsage::getUsed));

    meter
        .upDownCounterBuilder("process.runtime.jvm.memory.init")
        .setDescription("Measure of initial memory requested")
        .setUnit("By")
        .buildWithCallback(callback(poolBeans, MemoryUsage::getInit));

    meter
        .upDownCounterBuilder("process.runtime.jvm.memory.committed")
        .setDescription("Measure of memory committed")
        .setUnit("By")
        .buildWithCallback(callback(poolBeans, MemoryUsage::getCommitted));

    meter
        .upDownCounterBuilder("process.runtime.jvm.memory.max")
        .setDescription("Measure of max obtainable memory")
        .setUnit("By")
        .buildWithCallback(callback(poolBeans, MemoryUsage::getMax));

    OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
    com.sun.management.OperatingSystemMXBean sunOsMXBean =
        (com.sun.management.OperatingSystemMXBean) osMXBean;
    meter
        .counterBuilder("process.runtime.jvm.cpu.time")
        .setDescription("CPU time spent by process")
        .setUnit("ms")
        .buildWithCallback(
            observableLongMeasurement -> observableLongMeasurement.record(sunOsMXBean.getProcessCpuTime()));
  }

  private static Consumer<ObservableLongMeasurement> callback(
      List<MemoryPoolMXBean> poolBeans, Function<MemoryUsage, Long> extractor) {
    List<Attributes> attributeSets = new ArrayList<>(poolBeans.size());
    for (MemoryPoolMXBean pool : poolBeans) {
      attributeSets.add(
          Attributes.builder()
              .put("pool", pool.getName())
              .put("type", memoryType(pool.getType()))
              .build());
    }

    return measurement -> {
      for (int i = 0; i < poolBeans.size(); i++) {
        Attributes attributes = attributeSets.get(i);
        long value = extractor.apply(poolBeans.get(i).getUsage());
        if (value != -1) {
          measurement.record(value, attributes);
        }
      }
    };
  }

  private static String memoryType(MemoryType memoryType) {
    switch (memoryType) {
      case HEAP:
        return "heap";
      case NON_HEAP:
        return "nonheap";
    }
    return "unknown";
  }
}
