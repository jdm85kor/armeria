/*
 * Copyright 2015 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.linecorp.armeria.common.util;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A set of configuration options and their respective values.
 *
 * @see AbstractOption
 * @see AbstractOptionValue
 */
public abstract class AbstractOptions {

    protected Map<AbstractOption<Object>, AbstractOptionValue<AbstractOption<Object>, Object>> valueMap;

    @SafeVarargs
    @SuppressWarnings("unchecked")
    protected <T extends AbstractOptionValue<?, ?>> AbstractOptions(
            AbstractOptions baseConstantValues, Function<T, T> validator, T... values) {

        valueMap = baseConstantValues == null ? new IdentityHashMap<>()
                                              : new IdentityHashMap<>(baseConstantValues.valueMap);
        if (validator == null) {
            validator = Function.identity();
        }
        if (values != null) {
            Stream.of(values)
                  .map(validator)
                  .forEach(v -> valueMap.put((AbstractOption<Object>) v.option(),
                                             (AbstractOptionValue<AbstractOption<Object>, Object>) v));
        }
    }

    protected <T extends AbstractOptionValue<?, ?>> AbstractOptions(
            AbstractOptions baseConstantValue, T[] values) {
        this(baseConstantValue, null, values);
    }

    @SuppressWarnings("unchecked")
    protected <O extends AbstractOption<V>, V> Optional<V> get0(AbstractOption<V> option) {
        @SuppressWarnings("rawtypes")
        AbstractOptionValue<O, V> optionValue =
                (AbstractOptionValue<O, V>) (AbstractOptionValue) valueMap.get(option);
        return optionValue == null ? Optional.empty() : Optional.of(optionValue.value());
    }

    @SuppressWarnings("unchecked")
    protected <O extends AbstractOption<V>, V> V getOrElse0(O option, V defaultValue) {
        return get0(option).orElse(defaultValue);
    }

    @SuppressWarnings("unchecked")
    protected <K, V> Map<K, V> asMap0() {
        return Collections.unmodifiableMap((Map<? extends K, ? extends V>) valueMap);
    }

    @Override
    public String toString() {
        return toString(asMap0().values());
    }

    static String toString(Collection<?> values) {
        return "OptionValues{" + values + '}';
    }
}
