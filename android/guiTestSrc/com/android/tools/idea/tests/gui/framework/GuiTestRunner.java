/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework;

import org.fest.swing.image.ScreenshotTaker;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.internal.AssumptionViolatedException;
import org.junit.internal.runners.model.ReflectiveCallable;
import org.junit.internal.runners.statements.Fail;
import org.junit.internal.runners.statements.RunAfters;
import org.junit.internal.runners.statements.RunBefores;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

import java.awt.*;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertNotNull;

public class GuiTestRunner extends BlockJUnit4ClassRunner {

  private TestClass myTestClass;

  @Nullable private final ScreenshotTaker myScreenshotTaker;

  public GuiTestRunner(Class<?> testClass) throws InitializationError {
    super(testClass);

    myScreenshotTaker = canRunGuiTests() ? new ScreenshotTaker() : null;
    try {
      // A random class which is reachable from module community-main's classpath but not
      // module android's classpath
      Class.forName("git4idea.repo.GitConfig", false, testClass.getClassLoader());
    }
    catch (ClassNotFoundException e) {
      throw new InitializationError("Invalid test run configuration. Edit your test configuration and make sure that " +
                                    "\"Use classpath of module\" is set to \"community-main\", *NOT* \"android\"!");
    }
  }

  @Override
  protected void runChild(final FrameworkMethod method, RunNotifier notifier) {
    if (!canRunGuiTests()) {
      notifier.fireTestAssumptionFailed(new Failure(describeChild(method), new AssumptionViolatedException("Headless environment")));
      System.out.println(String.format("Skipping test '%1$s'. UI tests cannot run in a headless environment.", method.getName()));
    } else if (MethodInvoker.doesIdeHaveFatalErrors()) {
      notifier.fireTestIgnored(describeChild(method)); // TODO: can we restart the IDE at this point, instead of giving up?
      System.out.println(String.format("Skipping test '%1$s': a fatal error has occurred in the IDE", method.getName()));
      notifier.pleaseStop();
    } else {
      super.runChild(method, notifier);
    }
  }

  @Override
  protected Statement methodBlock(FrameworkMethod method) {
    FrameworkMethod newMethod;
    try {
      loadClassesWithIdeClassLoader();
      Method methodFromClassLoader = myTestClass.getJavaClass().getMethod(method.getName());
      newMethod = new FrameworkMethod(methodFromClassLoader);
    }
    catch (Exception e) {
      return new Fail(e);
    }
    Object test;
    try {
      test = new ReflectiveCallable() {
        @Override
        protected Object runReflectiveCall() throws Throwable {
          return createTest();
        }
      }.run();
    }
    catch (Throwable e) {
      return new Fail(e);
    }

    Statement statement = methodInvoker(newMethod, test);

    List<FrameworkMethod> beforeMethods = myTestClass.getAnnotatedMethods(Before.class);
    if (!beforeMethods.isEmpty()) {
      statement = new RunBefores(statement, beforeMethods, test);
    }

    List<FrameworkMethod> afterMethods = myTestClass.getAnnotatedMethods(After.class);
    if (!afterMethods.isEmpty()) {
      statement = new RunAfters(statement, afterMethods, test);
    }

    return statement;
  }

  public static boolean canRunGuiTests() {
    return !GraphicsEnvironment.isHeadless();
  }

  private void loadClassesWithIdeClassLoader() throws Exception {
    ClassLoader ideClassLoader = IdeTestApplication.getInstance().getIdeClassLoader();
    Thread.currentThread().setContextClassLoader(ideClassLoader);

    Class<?> testClass = getTestClass().getJavaClass();
    myTestClass = new TestClass(ideClassLoader.loadClass(testClass.getName()));
  }

  @Override
  protected Object createTest() throws Exception {
    return myTestClass != null ? myTestClass.getJavaClass().newInstance() : super.createTest();
  }

  @Override
  protected Statement methodInvoker(final FrameworkMethod method, Object test) {
    try {
      assertNotNull(myScreenshotTaker);
      return new MethodInvoker(method, test, myScreenshotTaker);
    }
    catch (Throwable e) {
      return new Fail(e);
    }
  }
}
