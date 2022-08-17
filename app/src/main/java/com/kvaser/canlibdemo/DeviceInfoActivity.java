package com.kvaser.canlibdemo;

import android.content.*;
import android.os.*;
import android.support.v7.app.AppCompatActivity;
import android.view.*;
import android.widget.*;
import com.kvaser.canlib.*;

/**
 * Presents info about currently connected Kvaser USB devices
 */
public class DeviceInfoActivity extends AppCompatActivity {
  
  private final IncomingHandler incomingHandler = new IncomingHandler();
  // Messenger that is registered with the background service
  final Messenger mMessenger = new Messenger(incomingHandler);
  // Messenger for communicating with service
  Messenger mService = null;
  // Flag indicating whether bind has been called on the service
  boolean mIsBound;
  
  private ServiceConnection mConnection = new ServiceConnection() {
    
    public void onServiceConnected(ComponentName className, IBinder service) {
      mService = new Messenger(service);
      try {
        Message msg = Message.obtain(null, CanLibService.MSG_REGISTER_CLIENT);
        msg.replyTo = mMessenger;
        mService.send(msg);
      } catch (RemoteException e) {
        // Failed to send message to server. Print trace and exit activity.
        e.printStackTrace();
        finish();
      }
    }
    
    public void onServiceDisconnected(ComponentName className) {
      mService = null;
    }
  };
  // Device list containing the number of channels of each device
  private int[] deviceList;
  // Bundle array containing info about devices
  private Bundle[] deviceInfo;
  // Array containing the bus parameters for each channel on each device
  private CanBusParams[][] channelParams;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_device_info);

    android.support.v7.widget.Toolbar toolbar =
        (android.support.v7.widget.Toolbar) findViewById(R.id.main_toolbar);
    setSupportActionBar(toolbar);

    doBindService();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    doUnbindService();
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
  
  void doBindService() {
    bindService(new Intent(this, CanLibService.class), mConnection, Context.BIND_AUTO_CREATE);
    mIsBound = true;
  }
  
  void doUnbindService() {
    if (mIsBound) {
      if (mService != null) {
        try {
          Message msg = Message.obtain(null, CanLibService.MSG_UNREGISTER_CLIENT);
          msg.replyTo = mMessenger;
          mService.send(msg);

        } catch (RemoteException e) {
          // Failed to send message to server. Print trace.
          e.printStackTrace();
        }
      }

      unbindService(mConnection);
      mIsBound = false;
    }
  }
  
  private void UpdateDeviceInfoView() {
    LinearLayout identifyBtnLayout = (LinearLayout) findViewById(R.id.identify_buttons_layout);
    TextView identifyLabel = (TextView) findViewById(R.id.identify_label);
    View identifySeparator = findViewById(R.id.identify_separator);
    
    // Remove all previous identify buttons
    for (int i = identifyBtnLayout.getChildCount(); i >= 0; i--) {
      View v = identifyBtnLayout.getChildAt(i);
      if (v instanceof Button) {
        identifyBtnLayout.removeViewAt(i);
      }
    }
    identifyLabel.setVisibility(View.GONE);
    identifyBtnLayout.setVisibility(View.GONE);
    identifySeparator.setVisibility(View.GONE);
    
    if (deviceList != null && deviceInfo != null) {
      // Add identify buttons
      for (int i = 0; i < deviceList.length; i++) {
        Button identifyButton = new Button(this);
        identifyButton.setId(i);
        final int id_ = identifyButton.getId();
        identifyButton.setText("Dev" + i);
        identifyButton.setOnClickListener(new View.OnClickListener() {
          public void onClick(View view) {
            incomingHandler.sendRequest(Message.obtain(null, CanLibService.MSG_FLASH_LEDS, id_, 0));
          }
        });
        identifyBtnLayout.addView(identifyButton, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
      }
      if (deviceList.length > 0) {
        identifyLabel.setVisibility(View.VISIBLE);
        identifyBtnLayout.setVisibility(View.VISIBLE);
        identifySeparator.setVisibility(View.VISIBLE);
      }

      // Update device info
      TextView infoText = (TextView) findViewById(R.id.info_text);
      infoText.setText("Number of connected devices: " + deviceList.length + "\n");

      for (int i = 0; i < deviceList.length; i++) {
        Bundle b = deviceInfo[i];
        if (b != null) {
          infoText.append("\n" + "Device index: " + i);
          infoText.append("\n" + "Number of channels: " + deviceList[i]);
          for (String key : b.keySet()) {
            infoText.append("\n" + key + ": " + b.getString(key));
          }
          infoText.append("\n");
        }

        if (channelParams != null) {
          if (i < channelParams.length) {
            if (channelParams[i] != null) {
              for (int j = 0; j < channelParams[i].length; j++) {
                if (channelParams[i][j] != null) {
                  infoText.append(
                      "\n" + "ch" + j + " bit rate: " + channelParams[i][j].bitRate + " b/s");
                  infoText.append(
                      "\n" + "ch" + j + " tseg1: " + channelParams[i][j].tseg1 + " time quanta");
                  infoText.append(
                      "\n" + "ch" + j + " tseg2: " + channelParams[i][j].tseg2 + " time quanta");
                  infoText.append("\n" + "ch" + j + " sjw: " + channelParams[i][j].sjw);
                  infoText.append("\n");
                }
              }
            }
          }
        }
      }
    }
  }
  
  /*
   * Handler of incoming messages from service.
   */
  class IncomingHandler extends Handler {
    
    @Override
    public void handleMessage(Message msg) {
      
      switch (msg.what) {
        
        // Send question of number of devices on registration ACK
        case CanLibService.MSG_REGISTER_CLIENT:
          Message registerMsg = Message.obtain(null, CanLibService.MSG_NUMBER_OF_DEVICES);
          sendRequest(registerMsg);
          break;
        
        // Re-initialize device lists
        case CanLibService.MSG_NUMBER_OF_DEVICES:
          deviceList = new int[msg.arg1];
          deviceInfo = new Bundle[msg.arg1];
          channelParams = new CanBusParams[deviceList.length][];
          if (deviceList.length == 0) {
            UpdateDeviceInfoView();
          } else {
            // Ask for info about devices
            for (int i = 0; i < deviceList.length; i++) {
              deviceList[i] = 0;
              Message numMsg = Message.obtain(null, CanLibService.MSG_NUMBER_OF_CHANNELS, i, 0);
              sendRequest(numMsg);
              Message infoMsg = Message.obtain(null, CanLibService.MSG_DEVICE_INFO, i, 0);
              sendRequest(infoMsg);
            }
          }
          break;
        
        // Populate device list
        case CanLibService.MSG_NUMBER_OF_CHANNELS:
          if (deviceList != null) {
            if (msg.arg1 < deviceList.length) {
              deviceList[msg.arg1] = msg.arg2;
              UpdateDeviceInfoView();
            }
          }
          if (channelParams != null) {
            if (msg.arg1 < channelParams.length) {
              channelParams[msg.arg1] = new CanBusParams[msg.arg2];
              for (int i = 0; i < channelParams[msg.arg1].length; i++) {
                Message paramsMsg =
                    Message.obtain(null, CanLibService.MSG_READ_PARAMETERS, msg.arg1, i);
                sendRequest(paramsMsg);
              }
            }
          }
          break;
        
        // Populate device info list
        case CanLibService.MSG_DEVICE_INFO:
          if (deviceList != null) {
            if (msg.arg1 < deviceList.length) {
              deviceInfo[msg.arg1] = msg.getData();
              UpdateDeviceInfoView();
            }
          }
          break;
        
        case CanLibService.MSG_READ_PARAMETERS:
          if (channelParams != null) {
            if (msg.arg1 < channelParams.length) {
              if (channelParams[msg.arg1] != null) {
                if (msg.arg2 < channelParams[msg.arg1].length) {
                  channelParams[msg.arg1][msg.arg2] = (CanBusParams) msg.obj;
                  UpdateDeviceInfoView();
                }
              }
            }
          }
          break;
        
        default:
          super.handleMessage(msg);
          break;
      }
    }
    
    private void sendRequest(Message msg) {
      try {
        msg.replyTo = mMessenger;
        mService.send(msg);
      } catch (RemoteException e) {
        // Failed to send message to server. Print trace and exit activity.
        e.printStackTrace();
        finish();
      }
    }
  }
}
