package com.foo;

import android.app.Activity;
import android.os.Bundle;

public class MyActivity extends Activity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    String text = getResources().getString(R.st<caret>ring.cancel);
  }
}
