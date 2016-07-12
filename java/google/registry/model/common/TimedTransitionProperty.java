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

package google.registry.model.common;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static google.registry.util.DateTimeUtils.latestOf;

import com.google.common.base.Function;
import com.google.common.collect.ForwardingMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.googlecode.objectify.mapper.Mapper;
import google.registry.model.ImmutableObject;
import google.registry.util.TypeUtils;
import java.util.NavigableMap;
import java.util.TreeMap;
import javax.annotation.Nullable;
import org.joda.time.DateTime;

/**
 * An entity property whose value transitions over time.  Each value it takes on becomes active
 * at a corresponding instant, and remains active until the next transition occurs.  At least one
 * "start of time" value (corresponding to START_OF_TIME, i.e. the Unix epoch) must be provided
 * so that the property will have a value for all possible times.
 *
 * <p>This concept is naturally represented by a sorted map of {@code DateTime} to {@code V}, but
 * the AppEngine datastore cannot natively represent a map keyed on non-strings.  Instead, we store
 * an ordered list of transitions and use Objectify's @Mapify annotation to automatically recreate
 * the sorted map on load from the datastore, which is used as a backing map for this property; the
 * property itself also implements Map by way of extending ForwardingMap, so that this property can
 * stored directly as the @Mapify field in the entity.
 *
 * <p>The type parameter {@code T} specifies a user-defined subclass of {@code TimedTransition<V>}
 * to use for storing the list of transitions.  The user is given this choice of subclass so that
 * the field of the value type stored in the transition can be given a customized name.
 */
public class TimedTransitionProperty<V, T extends TimedTransitionProperty.TimedTransition<V>>
    extends ForwardingMap<DateTime, T> {

  /**
   * A transition to a value of type {@code V} at a certain time.  This superclass only has a field
   * for the {@code DateTime}, which means that subclasses should supply the field of type {@code V}
   * and implementations of the abstract getter and setter methods to access that field. This design
   * is so that subclasses tagged with @Embed can define a custom field name for their value, for
   * the purpose of backwards compatibility and better readability of the datastore representation.
   *
   * <p>The public visibility of this class exists only so that it can be subclassed; clients should
   * never call any methods on this class or attempt to access its members, but should instead treat
   * it as a customizable implementation detail of {@code TimedTransitionProperty}.  However, note
   * that subclasses must also have public visibility so that they can be instantiated via
   * reflection in a call to {@code fromValueMap}.
   */
  public abstract static class TimedTransition<V> extends ImmutableObject {
    /** The time at which this value becomes the active value. */
    private DateTime transitionTime;

    /** Returns the value that this transition will activate. */
    protected abstract V getValue();

    /** Sets the value that will be activated at this transition's time. */
    protected abstract void setValue(V value);
  }

  /** Mapper used with @Mapify extracting time from TimedTransition to use as key. */
  public static class TimeMapper implements Mapper<DateTime, TimedTransition<?>> {
    @Override
    public DateTime getKey(TimedTransition<?> transition) {
      return transition.transitionTime;
    }
  }

  /**
   * Converts the provided value map into the equivalent transition map, using transition objects
   * of the given TimedTransition subclass.  The value map must be sorted according to the natural
   * ordering of its DateTime keys, and keys cannot be earlier than START_OF_TIME.
   */
  // NB: The Class<T> parameter could be eliminated by getting the class via reflection, but then
  // the callsite cannot infer T, so unless you explicitly call this as .<V, T>fromValueMap() it
  // will default to using just TimedTransition<V>, which fails at runtime.
  private static <V, T extends TimedTransition<V>> NavigableMap<DateTime, T> makeTransitionMap(
      ImmutableSortedMap<DateTime, V> valueMap,
      final Class<T> timedTransitionSubclass) {
    checkArgument(
        Ordering.natural().equals(valueMap.comparator()),
        "Timed transition value map must have transition time keys in chronological order");
    return Maps.transformEntries(valueMap, new Maps.EntryTransformer<DateTime, V, T>() {
        // For each entry in the input value map, make the output map have an entry at the
        // corresponding time that points to a transition containing that time and that value.
        @Override
        public T transformEntry(DateTime transitionTime, V value) {
          checkArgument(!transitionTime.isBefore(START_OF_TIME),
              "Timed transition times cannot be earlier than START_OF_TIME / Unix Epoch");
          T subclass = TypeUtils.instantiate(timedTransitionSubclass);
          ((TimedTransition<V>) subclass).transitionTime = transitionTime;
          subclass.setValue(value);
          return subclass;
        }});
  }

  /**
   * Returns a new immutable {@code TimedTransitionProperty} representing the given map of DateTime
   * to value, with transitions constructed using the given {@code TimedTransition} subclass.
   *
   * <p>This method should be the normal method for constructing a {@link TimedTransitionProperty}.
   */
  public static <V, T extends TimedTransition<V>> TimedTransitionProperty<V, T> fromValueMap(
      ImmutableSortedMap<DateTime, V> valueMap,
      final Class<T> timedTransitionSubclass) {
    return new TimedTransitionProperty<>(ImmutableSortedMap.copyOf(
        makeTransitionMap(valueMap, timedTransitionSubclass)));
  }

  /**
   * Returns a new mutable {@code TimedTransitionProperty} representing the given map of DateTime
   * to value, with transitions constructed using the given {@code TimedTransition} subclass.
   *
   * <p>This method should only be used for initializing fields that are declared with the
   * @Mapify annotation. The map for those fields must be mutable so that Objectify can load values
   * from the datastore into the map, but clients should still never mutate the field's map
   * directly.
   */
  public static <V, T extends TimedTransition<V>> TimedTransitionProperty<V, T> forMapify(
      ImmutableSortedMap<DateTime, V> valueMap,
      Class<T> timedTransitionSubclass) {
    return new TimedTransitionProperty<>(
        new TreeMap<>(makeTransitionMap(valueMap, timedTransitionSubclass)));
  }

  /**
   * Returns a new mutable {@code TimedTransitionProperty} representing the given value being set at
   * start of time, constructed using the given {@code TimedTransition} subclass.
   *
   * <p>This method should only be used for initializing fields that are declared with the
   * @Mapify annotation. The map for those fields must be mutable so that Objectify can load values
   * from the datastore into the map, but clients should still never mutate the field's map
   * directly.
   */
  public static <V, T extends TimedTransition<V>> TimedTransitionProperty<V, T> forMapify(
      V valueAtStartOfTime, Class<T> timedTransitionSubclass) {
    return forMapify(
        ImmutableSortedMap.of(START_OF_TIME, valueAtStartOfTime), timedTransitionSubclass);
  }

  /** The backing map of DateTime to TimedTransition subclass used to store the transitions. */
  private final NavigableMap<DateTime, T> backingMap;

  /** Returns a new {@code TimedTransitionProperty} backed by the provided map instance. */
  private TimedTransitionProperty(NavigableMap<DateTime, T> backingMap) {
    checkArgument(backingMap.get(START_OF_TIME) != null,
        "Must provide transition entry for the start of time (Unix Epoch)");
    this.backingMap = backingMap;
  }

  /**
   * Checks whether this TimedTransitionProperty is in a valid state, i.e. whether it has a
   * transition entry for START_OF_TIME, and throws IllegalStateException if not.
   */
  public void checkValidity() {
    checkState(backingMap.get(START_OF_TIME) != null,
        "Timed transition values missing required entry for the start of time (Unix Epoch)");
  }

  @Override
  protected NavigableMap<DateTime, T> delegate() {
    return backingMap;
  }

  /** Returns the map of DateTime to value that is the "natural" representation of this property. */
  public ImmutableSortedMap<DateTime, V> toValueMap() {
    return ImmutableSortedMap.copyOfSorted(Maps.transformValues(
        backingMap,
        new Function<T, V>() {
          @Override
          public V apply(T timedTransition) {
            return timedTransition.getValue();
          }}));
  }

  /**
   * Returns the value of the property that is active at the specified time.  The active value for
   * a time before START_OF_TIME is extrapolated to be the value that is active at START_OF_TIME.
   */
  public V getValueAtTime(DateTime time) {
    // Retrieve the current value by finding the latest transition before or at the given time,
    // where any given time earlier than START_OF_TIME is replaced by START_OF_TIME.
    return backingMap.floorEntry(latestOf(START_OF_TIME, time)).getValue().getValue();
  }

  /**
   * Returns the time of the next transition.  Returns null if there is no subsequent transition.
   */
  @Nullable
  public DateTime getNextTransitionAfter(DateTime time) {
    return backingMap.higherKey(latestOf(START_OF_TIME, time));
  }
}
