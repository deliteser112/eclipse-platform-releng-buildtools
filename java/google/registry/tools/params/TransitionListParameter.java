// Copyright 2016 The Domain Registry Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.tools.params;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Ordering;
import google.registry.model.registry.Registry.TldState;
import java.util.Map;
import org.joda.money.Money;
import org.joda.time.DateTime;

/**
 * Combined converter and validator class for transition list JCommander argument strings.
 *
 * <p>These strings have the form {@code <DateTime>=<T-str>,[<DateTime>=<T-str>]*} where
 * {@code <T-str>} is a string that can be parsed into an instance of some value type {@code T}, and
 * the entire argument represents a series of timed transitions of some property taking on those
 * values. This class converts such a string into an ImmutableSortedMap mapping DateTime to
 * {@code T}. Validation and conversion share the same logic; validation is just done by attempting
 * conversion and throwing exceptions if need be.
 *
 * <p>Subclasses must implement parseValue() to define how to parse {@code <T-str>} into a
 * {@code T}.
 *
 * @param <T> instance value type
 */
// TODO(b/19031334): Investigate making this complex generic type work with the factory.
public abstract class TransitionListParameter<T>
    extends ParameterConverterValidator<ImmutableSortedMap<DateTime, T>> {

  private static final DateTimeParameter DATE_TIME_CONVERTER = new DateTimeParameter();

  public TransitionListParameter() {
    super("Not formatted correctly or has transition times out of order.");
  }

  /** Override to define how to parse rawValue into an object of type T. */
  protected abstract T parseValue(String rawValue);

  @Override
  public ImmutableSortedMap<DateTime, T> convert(String transitionListString) {
    ImmutableMap.Builder<DateTime, T> builder = new ImmutableMap.Builder<>();
    for (Map.Entry<String, String> entry :
        Splitter.on(',').withKeyValueSeparator('=').split(transitionListString).entrySet()) {
      builder.put(
          DATE_TIME_CONVERTER.convert(entry.getKey()),
          parseValue(entry.getValue()));
    }
    ImmutableMap<DateTime, T> transitionMap = builder.build();
    checkArgument(Ordering.natural().isOrdered(transitionMap.keySet()),
        "Transition times out of order.");
    return ImmutableSortedMap.copyOf(transitionMap);
  }

  /** Converter-validator for TLD state transitions. */
  public static class TldStateTransitions extends TransitionListParameter<TldState> {
    @Override
    protected TldState parseValue(String value) {
      return TldState.valueOf(value);
    }
  }

  /** Converter-validator for billing cost transitions. */
  public static class BillingCostTransitions extends TransitionListParameter<Money> {
    private static final MoneyParameter MONEY_CONVERTER = new MoneyParameter();

    @Override
    protected Money parseValue(String value) {
      return MONEY_CONVERTER.convert(value);
    }
  }
}
