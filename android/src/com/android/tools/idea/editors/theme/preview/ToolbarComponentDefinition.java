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
package com.android.tools.idea.editors.theme.preview;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static com.android.SdkConstants.*;


/**
 * Toolbar component definition used for both the framework and appcompat toolbars.
 */
class ToolbarComponentDefinition extends ThemePreviewBuilder.ComponentDefinition {
  private boolean myIsAppCompat;

  ToolbarComponentDefinition(boolean isAppCompat) {
    super("Toolbar", ThemePreviewBuilder.ComponentGroup.TOOLBAR, isAppCompat ? "android.support.v7.widget.Toolbar" : "Toolbar");

    myIsAppCompat = isAppCompat;

    if (!isAppCompat) {
      setApiLevel(21);
      set(ATTR_TITLE, "Toolbar");
      set("navigationIcon", "@drawable/abc_ic_ab_back_mtrl_am_alpha");
      set(ATTR_LAYOUT_HEIGHT, "?android:attr/actionBarSize");
    }
    else {
      // We are trying to emulate the behaviour of the bar system components using regular content. We remove the content insets so the
      // buttons are correctly located.
      set(APP_PREFIX, "contentInsetStart", "0dp");
      set(APP_PREFIX, "contentInsetLeft", "0dp");
    }

    String attrPrefix = getAttrPrefix(isAppCompat);
    set(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT);
    set(ATTR_LAYOUT_HEIGHT, attrPrefix + "actionBarSize");
    set(ATTR_BACKGROUND, attrPrefix + "colorPrimary");

    addAlias("Actionbar");
  }

  private static String getAttrPrefix(boolean isAppCompat) {
    return isAppCompat ? ATTR_REF_PREFIX : "?android:attr/";
  }

  @Override
  Element build(@NotNull Document document) {
    Element toolbarComponent = super.build(document);

    if (myIsAppCompat) {
      Element navIcon = document.createElement(IMAGE_BUTTON);
      navIcon.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
      navIcon.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT, "?attr/actionBarSize");
      navIcon.setAttribute("style", "?attr/toolbarNavigationButtonStyle");
      navIcon.setAttributeNS(ANDROID_URI, ATTR_SRC, "@drawable/abc_ic_ab_back_mtrl_am_alpha");
      toolbarComponent.appendChild(navIcon);

      // Create a title using the same values that the Toolbar title has when created programmatically.
      Element title = document.createElement(TEXT_VIEW);
      title.setAttributeNS(ANDROID_URI, ATTR_TEXT, "Toolbar");
      title.setAttributeNS(ANDROID_URI, "textAppearance", "@style/TextAppearance.Widget.AppCompat.Toolbar.Title");
      title.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
      title.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT, "?attr/actionBarSize");
      title.setAttributeNS(ANDROID_URI, ATTR_GRAVITY, VALUE_CENTER_VERTICAL);
      title.setAttributeNS(ANDROID_URI, "ellipsize", "end");
      title.setAttributeNS(ANDROID_URI, ATTR_SINGLE_LINE, VALUE_TRUE);
      toolbarComponent.appendChild(title);
    }

    String attrPrefix = getAttrPrefix(myIsAppCompat);
    Element menuIcon = document.createElement(IMAGE_BUTTON);
    menuIcon.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH, "40dp");
    menuIcon.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT, attrPrefix + "actionBarSize");
    menuIcon.setAttributeNS(ANDROID_URI, ATTR_BACKGROUND, attrPrefix + "selectableItemBackground");
    menuIcon.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY, GRAVITY_VALUE_RIGHT);
    menuIcon.setAttributeNS(ANDROID_URI, ATTR_SRC, "@drawable/abc_ic_menu_moreoverflow_mtrl_alpha");
    toolbarComponent.appendChild(menuIcon);

    return toolbarComponent;
  }
}
