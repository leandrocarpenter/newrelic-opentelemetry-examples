package io.opentelemetry.sdk.metrics.view;

import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.internal.aggregator.Aggregator;
import io.opentelemetry.sdk.metrics.internal.aggregator.AggregatorFactory;
import io.opentelemetry.sdk.metrics.internal.aggregator.Util;
import io.opentelemetry.sdk.metrics.internal.descriptor.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.internal.exemplar.ExemplarFilter;

public class ExponentialHistogramAggregation implements AggregatorFactory, Aggregation {

  public ExponentialHistogramAggregation() {}

  @Override
  public boolean isCompatibleWithInstrument(InstrumentDescriptor instrumentDescriptor) {
    return ((AggregatorFactory) Aggregation.explicitBucketHistogram())
        .isCompatibleWithInstrument(instrumentDescriptor);
  }

  @Override
  public String toString() {
    return "ExponentialHistogramAggregation";
  }

  @Override
  public <T> Aggregator<T> createAggregator(
      InstrumentDescriptor instrumentDescriptor, ExemplarFilter exemplarFilter) {
    return Util.createDoubleExponentialHistogramAggregator();
  }
}
