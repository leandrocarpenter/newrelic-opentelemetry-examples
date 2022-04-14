package io.opentelemetry.sdk.metrics.internal.aggregator;

import io.opentelemetry.sdk.metrics.internal.exemplar.ExemplarReservoir;

public class Util {

  @SuppressWarnings("unchecked")
  public static <T> Aggregator<T> createDoubleExponentialHistogramAggregator() {
    return (Aggregator<T>) new DoubleExponentialHistogramAggregator(ExemplarReservoir::noSamples);
  }
}
