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
package com.android.tools.idea.gradle.util;

import com.android.SdkConstants;
import com.google.common.base.Joiner;
import com.google.common.io.Closeables;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

/**
 * Utility methods related to a Gradle project's local.properties file.
 */
public final class LocalProperties {
  private static final String SDK_DIR_PROPERTY = "sdk.dir";

  private LocalProperties() {
  }

  /**
   * Returns the path of the Android SDK specified in the project's local.properties file.
   *
   * @param project the given project.
   * @return the path of the Android SDK specified in the project's local.properties file; or {@code null} if the given project does not
   *         have a local.properties file or if the file does not specify the path of the Android SDK to use.
   * @throws IOException if an I/O error occurs while reading the file.
   */
  @Nullable
  public static String getAndroidSdkPath(@NotNull Project project) throws IOException {
    Properties properties = readFile(project);
    if (properties == null) {
      return null;
    }
    return properties.getProperty(SDK_DIR_PROPERTY);
  }

  /**
   * Returns the contents of the local.properties file in the given project.
   *
   * @param project the given project.
   * @return the contents of the local.properties file in the given project, or {@code null} if such file does not exist.
   * @throws IOException if an I/O error occurs while reading the file.
   */
  @Nullable
  public static Properties readFile(@NotNull Project project) throws IOException {
    File filePath = localPropertiesFilePath(project);
    if (!filePath.isFile()) {
      return null;
    }
    Properties properties = new Properties();
    FileInputStream fileInputStream = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      fileInputStream = new FileInputStream(filePath);
      properties.load(fileInputStream);
    } catch (FileNotFoundException e) {
      return null;
    } finally {
      Closeables.closeQuietly(fileInputStream);
    }
    return properties;
  }

  /**
   * Creates a local.properties file, containing the path of the given Android SDK, inside the root directory of the given project.
   *
   * @param project    the given project.
   * @param androidSdk the Android SDK.
   * @throws IOException if an I/O error occurs while writing the contents of the file.
   */
  public static void createFile(@NotNull Project project, @NotNull Sdk androidSdk) throws IOException {
    File filePath = localPropertiesFilePath(project);
    FileUtilRt.createIfNotExists(filePath);
    // TODO: create this file using a template and just populate the path of Android SDK.
    String[] lines = {
      "# This file is automatically generated by Android Studio.",
      "# Do not modify this file -- YOUR CHANGES WILL BE ERASED!",
      "#",
      "# This file must *NOT* be checked into Version Control Systems,",
      "# as it contains information specific to your local configuration.",
      "",
      "# Location of the SDK. This is only used by Gradle.",
      "# For customization when using a Version Control System, please read the",
      "# header note.",
      SDK_DIR_PROPERTY + "=" + androidSdk.getHomePath()
    };
    String contents = Joiner.on(SystemProperties.getLineSeparator()).join(lines);
    FileUtil.writeToFile(filePath, contents);
  }

  @NotNull
  private static File localPropertiesFilePath(@NotNull Project project) {
    return new File(project.getBasePath(), SdkConstants.FN_LOCAL_PROPERTIES);
  }
}
