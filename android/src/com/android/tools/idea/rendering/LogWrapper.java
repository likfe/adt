/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.annotations.NonNull;
import com.android.utils.ILogger;
import com.intellij.openapi.diagnostic.Logger;

public class LogWrapper implements ILogger {
  private final Logger myLog;

  public LogWrapper(Logger log) {
    myLog = log;
  }

  @Override
  public void warning(String warningFormat, Object... args) {
    if (warningFormat != null) {
      myLog.debug(String.format(warningFormat, args));
    }
  }

  @Override
  public void info(@NonNull String msgFormat, Object... args) {
    if (msgFormat != null) {
      myLog.debug(String.format(msgFormat, args));
    }
  }

  @Override
  public void verbose(@NonNull String msgFormat, Object... args) {
  }

  @Override
  public void error(Throwable t, String errorFormat, Object... args) {
    if (t != null) {
      myLog.debug(t);
    }
    if (errorFormat != null) {
      String message = String.format(errorFormat, args);
      myLog.debug(message);
    }
  }
}
