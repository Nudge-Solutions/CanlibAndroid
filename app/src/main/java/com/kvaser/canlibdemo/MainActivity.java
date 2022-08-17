package com.kvaser.canlibdemo;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.*;
import android.view.*;

public class MainActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    Toolbar toolbar = (Toolbar) findViewById(R.id.main_toolbar);
    setSupportActionBar(toolbar);

    // Start the CanLib background service
    Intent intent = new Intent(MainActivity.this, CanLibService.class);
    startService(intent);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.toolbar_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Start parameters activity if settings button was selected
    if (item.getItemId() == R.id.action_settings) {
      Intent intent = new Intent(this, ParametersActivity.class);
      startActivity(intent);
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  public void openDeviceInfoActivity(View view) {
    Intent intent = new Intent(this, DeviceInfoActivity.class);
    startActivity(intent);
  }
  
  public void openOperationsActivity(View view) {
    Intent intent = new Intent(this, OperationsActivity.class);
    startActivity(intent);
  }
  
  public void openLogActivity(View view) {
    Intent intent = new Intent(this, LogActivity.class);
    startActivity(intent);
  }
}
