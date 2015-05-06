/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.properties.expressions.bool;

import com.android.tools.idea.properties.ObservableValue;
import com.android.tools.idea.properties.expressions.Expression;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for boolean expressions, providing a default implementation for the {@link BooleanExpression} interface.
 */
public abstract class AbstractBooleanExpression extends Expression<Boolean> implements BooleanExpression {

  protected AbstractBooleanExpression(ObservableValue... values) {
    super(values);
  }

  @NotNull
  @Override
  public final BooleanExpression not() {
    return BooleanExpressions.not(this);
  }

  @NotNull
  @Override
  public final BooleanExpression or(@NotNull ObservableValue<Boolean> other) {
    return new OrExpression(this, other);
  }

  @NotNull
  @Override
  public final BooleanExpression and(@NotNull ObservableValue<Boolean> other) {
    return new AndExpression(this, other);
  }
}
