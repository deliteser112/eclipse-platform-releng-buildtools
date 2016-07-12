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

package google.registry.ui.forms;

import static com.google.common.collect.Range.atLeast;
import static com.google.common.collect.Range.atMost;
import static com.google.common.collect.Range.closed;
import static com.google.common.truth.Truth.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.testing.NullPointerTester;
import com.google.re2j.Pattern;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FormField}. */
@RunWith(JUnit4.class)
public class FormFieldTest {

  private enum ICanHazEnum { LOL, CAT }

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void testConvert_nullString_notPresent() {
    assertThat(FormField.named("lol").build().convert(null)).isAbsent();
  }

  @Test
  public void testConvert_emptyString_returnsEmpty() {
    assertThat(FormField.named("lol").build().convert("").get()).isEmpty();
  }

  @Test
  public void testWithDefault_hasValue_returnsValue() {
    assertThat(FormField.named("lol").withDefault("default").build().convert("return me!"))
        .hasValue("return me!");
  }

  @Test
  public void testWithDefault_nullValue_returnsDefault() {
    assertThat(FormField.named("lol").withDefault("default").build().convert(null))
        .hasValue("default");
  }

  @Test
  public void testEmptyToNull_emptyString_notPresent() {
    assertThat(FormField.named("lol").emptyToNull().build().convert("")).isAbsent();
  }

  @Test
  public void testEmptyToNullRequired_emptyString_throwsFfe() {
    thrown.expect(equalTo(new FormFieldException("This field is required.").propagate("lol")));
    FormField.named("lol").emptyToNull().required().build().convert("");
  }

  @Test
  public void testEmptyToNull_typeMismatch() {
    thrown.expect(IllegalStateException.class);
    FormField.named("lol", Object.class).emptyToNull();
  }

  @Test
  public void testNamedLong() {
    assertThat(FormField.named("lol", Long.class).build().convert(666L)).hasValue(666L);
  }

  @Test
  public void testUppercased() {
    FormField<String, String> field = FormField.named("lol").uppercased().build();
    assertThat(field.convert(null)).isAbsent();
    assertThat(field.convert("foo")).hasValue("FOO");
    assertThat(field.convert("BAR")).hasValue("BAR");
  }

  @Test
  public void testLowercased() {
    FormField<String, String> field = FormField.named("lol").lowercased().build();
    assertThat(field.convert(null)).isAbsent();
    assertThat(field.convert("foo")).hasValue("foo");
    assertThat(field.convert("BAR")).hasValue("bar");
  }

  @Test
  public void testIn_passesThroughNull() {
    FormField<String, String> field = FormField.named("lol")
        .in(ImmutableSet.of("foo", "bar"))
        .build();
    assertThat(field.convert(null)).isAbsent();
  }

  @Test
  public void testIn_valueIsContainedInSet() {
    FormField<String, String> field = FormField.named("lol")
        .in(ImmutableSet.of("foo", "bar"))
        .build();
    assertThat(field.convert("foo")).hasValue("foo");
    assertThat(field.convert("bar")).hasValue("bar");
  }

  @Test
  public void testIn_valueMissingFromSet() {
    FormField<String, String> field = FormField.named("lol")
        .in(ImmutableSet.of("foo", "bar"))
        .build();
    thrown.expect(equalTo(new FormFieldException("Unrecognized value.").propagate("lol")));
    field.convert("omfg");
  }

  @Test
  public void testRange_hasLowerBound_nullValue_passesThrough() {
    assertThat(FormField.named("lol").range(atLeast(5)).build().convert(null)).isAbsent();
  }

  @Test
  public void testRange_minimum_stringLengthEqualToMinimum_doesNothing() {
    assertThat(FormField.named("lol").range(atLeast(5)).build().convert("hello")).hasValue("hello");
  }

  @Test
  public void testRange_minimum_stringLengthShorterThanMinimum_throwsFfe() {
    thrown.expect(FormFieldException.class);
    thrown.expectMessage("Number of characters (3) not in range [4");
    FormField.named("lol").range(atLeast(4)).build().convert("lol");
  }

  @Test
  public void testRange_noLowerBound_nullValue_passThrough() {
    assertThat(FormField.named("lol").range(atMost(5)).build().convert(null)).isAbsent();
  }

  @Test
  public void testRange_maximum_stringLengthEqualToMaximum_doesNothing() {
    assertThat(FormField.named("lol").range(atMost(5)).build().convert("hello")).hasValue("hello");
  }

  @Test
  public void testRange_maximum_stringLengthShorterThanMaximum_throwsFfe() {
    thrown.expect(FormFieldException.class);
    thrown.expectMessage("Number of characters (6) not in range");
    FormField.named("lol").range(atMost(5)).build().convert("omgomg");
  }

  @Test
  public void testRange_numericTypes() {
    FormField.named("lol", Byte.class).range(closed(5, 10)).build().convert((byte) 7);
    FormField.named("lol", Short.class).range(closed(5, 10)).build().convert((short) 7);
    FormField.named("lol", Integer.class).range(closed(5, 10)).build().convert(7);
    FormField.named("lol", Long.class).range(closed(5, 10)).build().convert(7L);
    FormField.named("lol", Float.class).range(closed(5, 10)).build().convert(7F);
    FormField.named("lol", Double.class).range(closed(5, 10)).build().convert(7D);
  }

  @Test
  public void testRange_typeMismatch() {
    thrown.expect(IllegalStateException.class);
    FormField.named("lol", Object.class).range(atMost(5));
  }

  @Test
  public void testMatches_matches_doesNothing() {
    assertThat(FormField.named("lol").matches(Pattern.compile("[a-z]+")).build().convert("abc"))
        .hasValue("abc");
  }

  @Test
  public void testMatches_mismatch_throwsFfeAndShowsDefaultErrorMessageWithPattern() {
    thrown.expect(equalTo(new FormFieldException("Must match pattern: [a-z]+").propagate("lol")));
    FormField.named("lol").matches(Pattern.compile("[a-z]+")).build().convert("123abc456");
  }

  @Test
  public void testMatches_typeMismatch() {
    thrown.expect(IllegalStateException.class);
    FormField.named("lol", Object.class).matches(Pattern.compile("."));
  }

  @Test
  public void testRetains() {
    assertThat(
            FormField.named("lol")
                .retains(CharMatcher.anyOf("0123456789"))
                .build()
                .convert(" 123  1593-43 453   45 4 4   \t"))
        .hasValue("1231593434534544");
  }

  @Test
  public void testCast() {
    assertThat(
            FormField.named("lol")
                .transform(
                    Integer.class,
                    new Function<String, Integer>() {
                      @Override
                      public Integer apply(String input) {
                        return Integer.parseInt(input);
                      }
                    })
                .build()
                .convert("123"))
        .hasValue(123);
  }

  @Test
  public void testCast_twice() {
    assertThat(
            FormField.named("lol")
                .transform(
                    Object.class,
                    new Function<String, Object>() {
                      @Override
                      public Integer apply(String input) {
                        return Integer.parseInt(input);
                      }
                    })
                .transform(String.class, Functions.toStringFunction())
                .build()
                .convert("123"))
        .hasValue("123");
  }

  @Test
  public void testAsList_null_notPresent() {
    assertThat(FormField.named("lol").asList().build().convert(null)).isAbsent();
  }

  @Test
  public void testAsList_empty_returnsEmpty() {
    assertThat(FormField.named("lol").asList().build().convert(ImmutableList.<String>of()).get())
        .isEmpty();
  }

  @Test
  public void testAsListEmptyToNullRequired_empty_throwsFfe() {
    thrown.expect(equalTo(new FormFieldException("This field is required.").propagate("lol")));
    FormField.named("lol")
        .asList()
        .emptyToNull()
        .required()
        .build()
        .convert(ImmutableList.<String>of());
  }

  @Test
  public void testListEmptyToNull_empty_notPresent() {
    assertThat(FormField.named("lol")
        .asList()
        .emptyToNull()
        .build()
        .convert(ImmutableList.<String>of()))
        .isAbsent();
  }

  @Test
  public void testAsEnum() {
    FormField<String, ICanHazEnum> omgField = FormField.named("omg")
        .asEnum(ICanHazEnum.class)
        .build();
    assertThat(omgField.convert("LOL").get()).isEqualTo(ICanHazEnum.LOL);
    assertThat(omgField.convert("CAT").get()).isEqualTo(ICanHazEnum.CAT);
  }

  @Test
  public void testAsEnum_lowercase_works() {
    FormField<String, ICanHazEnum> omgField = FormField.named("omg")
        .asEnum(ICanHazEnum.class)
        .build();
    assertThat(omgField.convert("lol").get()).isEqualTo(ICanHazEnum.LOL);
    assertThat(omgField.convert("cat").get()).isEqualTo(ICanHazEnum.CAT);
  }

  @Test
  public void testAsEnum_badInput_throwsFfe() {
    FormField<String, ICanHazEnum> omgField = FormField.named("omg")
        .asEnum(ICanHazEnum.class)
        .build();
    thrown.expect(equalTo(new FormFieldException("Enum ICanHazEnum does not contain 'helo'")
        .propagate("omg")));
    omgField.convert("helo");
  }

  @Test
  public void testSplitList() {
    FormField<String, List<String>> field = FormField.named("lol")
        .asList(Splitter.on(',').omitEmptyStrings())
        .build();
    assertThat(field.convert("oh,my,goth").get())
        .containsExactly("oh", "my", "goth")
        .inOrder();
    assertThat(field.convert("").get()).isEmpty();
    assertThat(field.convert(null)).isAbsent();
  }

  @Test
  public void testSplitSet() {
    FormField<String, Set<String>> field = FormField.named("lol")
        .uppercased()
        .asSet(Splitter.on(',').omitEmptyStrings())
        .build();
    assertThat(field.convert("oh,my,goth").get())
        .containsExactly("OH", "MY", "GOTH")
        .inOrder();
    assertThat(field.convert("").get()).isEmpty();
    assertThat(field.convert(null)).isAbsent();
  }

  @Test
  public void testAsList() {
    assertThat(
        FormField.named("lol").asList().build().convert(ImmutableList.of("lol", "cat", "")).get())
            .containsExactly("lol", "cat", "").inOrder();
  }

  @Test
  public void testAsList_trimmedEmptyToNullOnItems() {
    assertThat(FormField.named("lol")
        .trimmed()
        .emptyToNull()
        .matches(Pattern.compile("[a-z]+"))
        .asList()
        .range(closed(1, 2))
        .build()
        .convert(ImmutableList.of("lol\n", "\tcat "))
        .get())
            .containsExactly("lol", "cat").inOrder();
  }

  @Test
  public void testAsList_nullElements_getIgnored() {
    assertThat(FormField.named("lol")
        .emptyToNull()
        .asList()
        .build()
        .convert(ImmutableList.of("omg", ""))
        .get())
            .containsExactly("omg");
  }

  @Test
  public void testAsListRequiredElements_nullElement_throwsFfeWithIndex() {
    thrown.expect(equalTo(new FormFieldException("This field is required.")
        .propagate(1)
        .propagate("lol")));
    FormField.named("lol")
        .emptyToNull()
        .required()
        .asList()
        .build()
        .convert(ImmutableList.of("omg", ""));
  }

  @Test
  public void testMapAsListRequiredElements_nullElement_throwsFfeWithIndexAndKey() {
    thrown.expect(equalTo(new FormFieldException("This field is required.")
        .propagate("cat")
        .propagate(0)
        .propagate("lol")));
    FormField.mapNamed("lol")
        .transform(String.class, new Function<Map<String, ?>, String>() {
          @Override
          public String apply(Map<String, ?> input) {
            return FormField.named("cat")
                .emptyToNull()
                .required()
                .build()
                .extractUntyped(input)
                .get();
          }})
        .asList()
        .build()
        .convert(ImmutableList.<Map<String, ?>>of(
            ImmutableMap.of("cat", "")));
  }

  @Test
  public void testAsListTrimmed_typeMismatch() {
    FormField.named("lol").trimmed().asList();
    thrown.expect(IllegalStateException.class);
    FormField.named("lol").asList().trimmed();
  }

  @Test
  public void testAsMatrix() {
    assertThat(
        FormField.named("lol", Integer.class)
            .transform(new Function<Integer, Integer>() {
              @Override
              public Integer apply(Integer input) {
                return input * 2;
              }})
            .asList()
            .asList()
            .build()
            .convert(Lists.cartesianProduct(ImmutableList.of(
                ImmutableList.of(1, 2),
                ImmutableList.of(3, 4))))
            .get())
                .containsExactly(
                    ImmutableList.of(2, 6),
                    ImmutableList.of(2, 8),
                    ImmutableList.of(4, 6),
                    ImmutableList.of(4, 8)).inOrder();
 }

  @Test
  public void testAsSet() {
    assertThat(FormField.named("lol")
        .asSet()
        .build()
        .convert(ImmutableList.of("lol", "cat", "cat"))
        .get())
            .containsExactly("lol", "cat");
  }

  @Test
  public void testTrimmed() {
    assertThat(FormField.named("lol").trimmed().build().convert(" \thello \t\n")).hasValue("hello");
  }

  @Test
  public void testTrimmed_typeMismatch() {
    thrown.expect(IllegalStateException.class);
    FormField.named("lol", Object.class).trimmed();
  }

  @Test
  public void testAsBuilder() {
    FormField<String, String> field = FormField.named("omg").uppercased().build();
    assertThat(field.name()).isEqualTo("omg");
    assertThat(field.convert("hello")).hasValue("HELLO");
    field = field.asBuilder().build();
    assertThat(field.name()).isEqualTo("omg");
    assertThat(field.convert("hello")).hasValue("HELLO");
  }

  @Test
  public void testAsBuilderNamed() {
    FormField<String, String> field = FormField.named("omg").uppercased().build();
    assertThat(field.name()).isEqualTo("omg");
    assertThat(field.convert("hello")).hasValue("HELLO");
    field = field.asBuilderNamed("bog").build();
    assertThat(field.name()).isEqualTo("bog");
    assertThat(field.convert("hello")).hasValue("HELLO");
  }

  @Test
  public void testNullness() {
    NullPointerTester tester = new NullPointerTester()
        .setDefault(Class.class, Object.class)
        .setDefault(Function.class, Functions.identity())
        .setDefault(Pattern.class, Pattern.compile("."))
        .setDefault(Range.class, Range.all())
        .setDefault(Map.class, ImmutableMap.of())
        .setDefault(Set.class, ImmutableSet.of())
        .setDefault(String.class, "hello.com");
    tester.testAllPublicStaticMethods(FormField.class);
    tester.testAllPublicInstanceMethods(FormField.named("lol"));
    tester.testAllPublicInstanceMethods(FormField.named("lol").build());
  }
}
