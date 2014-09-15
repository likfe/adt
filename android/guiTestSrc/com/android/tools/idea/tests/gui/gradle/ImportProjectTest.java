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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.annotation.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.FileChooserDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class ImportProjectTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testImportProjectWithoutWrapper() throws IOException {
    File projectDirPath = copyProjectBeforeOpening("AarDependency");

    IdeFrameFixture.deleteWrapper(projectDirPath);

    cleanUpProjectForImport(projectDirPath);

    // Import project
    findWelcomeFrame().clickImportProjectButton();
    FileChooserDialogFixture importProjectDialog = FileChooserDialogFixture.findImportProjectDialog(myRobot);
    importProjectDialog.select(projectDirPath).clickOk();

    // Expect message suggesting to use Gradle wrapper. Click "OK" to use wrapper.
    GradleSyncMessageDialogFixture.find(myRobot).clickOk();

    IdeFrameFixture projectFrame = findIdeFrame(projectDirPath);
    projectFrame.waitForGradleProjectSyncToFinish()
                .requireGradleWrapperSet();
  }
}