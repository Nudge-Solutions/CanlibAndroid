package com.kvaser.canlibdemo;

import android.content.*;
import android.os.*;
import android.support.v4.app.*;
import android.support.v7.app.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.AdapterView.*;
import com.kvaser.canlib.*;

/**
 * Performs the logic behind the UI view which enables CAN channel operations.
 */
public class OperationsActivity extends AppCompatActivity {

  // Messenger for communicating with service.
  Messenger mService = null;
  // Flag indicating whether bind has been called on the service.
  boolean mIsBound;

  // Messenger which is registered with the background service */
  private Messenger mMessenger;
  private IncomingHandler incomingHandler;

  private ServiceConnection mConnection = new ServiceConnection() {

    public void onServiceConnected(ComponentName className, IBinder service) {
      mIsBound = true;
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

  private UiData uiData;
  private int[] numberOfChannelsPerDevice = new int[] {};
  private Bundle[] deviceInfo;
  private int selectedDevice = 0;
  private int selectedChannel = 0;
  private int newSelectedDevice = 0;
  private int newSelectedChannel = 0;
  private IdTypes idType = IdTypes.STANDARD;
  private Switch busSwitch;
  private Spinner deviceSpinner, channelSpinner, typeSpinner;
  private ArrayAdapter<String> deviceSpinnerAdapter, channelSpinnerAdapter, typeSpinnerAdapter;
  private FragmentManager fragmentManager;

  /*
   * Converts 16 ascii-coded hexadecimal numbers into 8 bytes
   */
  static byte[] parseAsciiCodedHex(String asciiString) {
    if (asciiString.length() == 16) {
      byte[] data = new byte[8];
      for (int i = 0; i < 8; i++) {
        data[i] = Byte.parseByte(asciiString.substring(2 * i + 1, 2 * i + 2), 16);
        data[i] |= Byte.parseByte(asciiString.substring(2 * i, 2 * i + 1), 16) << 4;
      }
      return data;
    } else {
      return null;
    }
  }

  /*
   * Tries to send the message in the EditText fields id and data.
   */
  public void sendCanMessage(View view) {
    EditText id = (EditText) findViewById(R.id.editText_id);
    EditText[] bytes = new EditText[8];
    bytes[0] = (EditText) findViewById(R.id.editText_byte7);
    bytes[1] = (EditText) findViewById(R.id.editText_byte6);
    bytes[2] = (EditText) findViewById(R.id.editText_byte5);
    bytes[3] = (EditText) findViewById(R.id.editText_byte4);
    bytes[4] = (EditText) findViewById(R.id.editText_byte3);
    bytes[5] = (EditText) findViewById(R.id.editText_byte2);
    bytes[6] = (EditText) findViewById(R.id.editText_byte1);
    bytes[7] = (EditText) findViewById(R.id.editText_byte0);

    char[] bytesAsChars = new char[16];
    int dlc = 0;
    for (int i = 7; i >= 0; i--) {
      if ((dlc == 0) && (bytes[i].getText().length() > 0)) {
        dlc = i + 1;
      }
      if (bytes[i].getText().length() == 2) {
        bytesAsChars[2 * i] = bytes[i].getText().charAt(0);
        bytesAsChars[2 * i + 1] = bytes[i].getText().charAt(1);
      } else if (bytes[i].getText().length() == 1) {
        bytesAsChars[2 * i] = '0';
        bytesAsChars[2 * i + 1] = bytes[i].getText().charAt(0);
      } else {
        bytesAsChars[2 * i] = '0';
        bytesAsChars[2 * i + 1] = '0';
      }
    }
    String dataString = String.copyValueOf(bytesAsChars);

    if (id.getText().length() != 0 && selectedDevice < numberOfChannelsPerDevice.length) {
      if (selectedChannel < numberOfChannelsPerDevice[selectedDevice]) {
        Message sendMsg =
            Message.obtain(null, CanLibService.MSG_SEND_MESSAGE, selectedDevice, selectedChannel);
        CanMessage msg = new CanMessage((int) Long.parseLong(id.getText().toString(), 16), dlc,
                                        parseAsciiCodedHex(dataString));
        if (idType == IdTypes.STANDARD) {
          msg.setFlag(CanMessage.MessageFlags.STANDARD_ID);
        } else {
          msg.setFlag(CanMessage.MessageFlags.EXTENDED_ID);
        }
        sendMsg.obj = msg;
        incomingHandler.sendRequest(sendMsg);
      }
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_operations);

    android.support.v7.widget.Toolbar toolbar =
        (android.support.v7.widget.Toolbar) findViewById(R.id.main_toolbar);
    setSupportActionBar(toolbar);

    uiData = (UiData) getApplicationContext();

    fragmentManager = getSupportFragmentManager();

    // Add a temporary fragment as a placeholder
    if (findViewById(R.id.fragment_container) != null) {
      fragmentManager.beginTransaction().add(R.id.fragment_container, new PrintoutFragment())
                     .commit();
    }

    // Set listeners for input fields
    EditText byte7 = (EditText) findViewById(R.id.editText_byte7);
    EditText byte6 = (EditText) findViewById(R.id.editText_byte6);
    EditText byte5 = (EditText) findViewById(R.id.editText_byte5);
    EditText byte4 = (EditText) findViewById(R.id.editText_byte4);
    EditText byte3 = (EditText) findViewById(R.id.editText_byte3);
    EditText byte2 = (EditText) findViewById(R.id.editText_byte2);
    EditText byte1 = (EditText) findViewById(R.id.editText_byte1);
    EditText byte0 = (EditText) findViewById(R.id.editText_byte0);
    byte7.addTextChangedListener(new byteFieldTextWatcher(byte7, byte6));
    byte6.addTextChangedListener(new byteFieldTextWatcher(byte6, byte5));
    byte5.addTextChangedListener(new byteFieldTextWatcher(byte5, byte4));
    byte4.addTextChangedListener(new byteFieldTextWatcher(byte4, byte3));
    byte3.addTextChangedListener(new byteFieldTextWatcher(byte3, byte2));
    byte2.addTextChangedListener(new byteFieldTextWatcher(byte2, byte1));
    byte1.addTextChangedListener(new byteFieldTextWatcher(byte1, byte0));

    // Populate device and channel spinners
    deviceSpinner = (Spinner) findViewById(R.id.device_spinner);
    channelSpinner = (Spinner) findViewById(R.id.channel_spinner);
    typeSpinner = (Spinner) findViewById(R.id.type_spinner);
    deviceSpinnerAdapter = new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item);
    channelSpinnerAdapter = new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item);
    typeSpinnerAdapter = new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item);
    deviceSpinner.setAdapter(deviceSpinnerAdapter);
    channelSpinner.setAdapter(channelSpinnerAdapter);
    typeSpinner.setAdapter(typeSpinnerAdapter);
    typeSpinnerAdapter.add("Standard");
    typeSpinnerAdapter.add("Extended");
    // Set spinner listeners
    deviceSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        newSelectedDevice = position;
        UpdateUI();
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });
    channelSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        newSelectedChannel = position;
        UpdateUI();
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });
    typeSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (position == 0) {
          idType = IdTypes.STANDARD;
        } else {
          idType = IdTypes.EXTENDED;
        }
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    busSwitch = (Switch) findViewById(R.id.busSwitch);
    busSwitch.setChecked(false);
    busSwitch.setText("Bus off");
    busSwitch.setOnCheckedChangeListener(new BusSwitchListener());

    incomingHandler = new IncomingHandler();
    mMessenger = new Messenger(incomingHandler);
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

  @Override
  protected void onPause() {
    super.onPause();

    doUnbindService();
  }
  
  @Override
  protected void onResume() {
    super.onResume();
    doBindService();
  }

  void doBindService() {
    bindService(new Intent(this, CanLibService.class), mConnection, Context.BIND_AUTO_CREATE);
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

  private void UpdateUI() {

    deviceSpinnerAdapter.clear();

    // Populate the device spinner
    for (int i = 0; i < numberOfChannelsPerDevice.length; i++) {
      if (deviceInfo[i] != null) {
        String serialNumber = deviceInfo[i].getString("Serial Number", "" + i);
        if (serialNumber =="0") {
          serialNumber = "virtual";
        }
        deviceSpinnerAdapter
            .add("DEV" + i + " (" + serialNumber + ")");
      } else {
        deviceSpinnerAdapter.add("DEV" + i);
      }
    }

    if (newSelectedDevice >= numberOfChannelsPerDevice.length) {
      newSelectedDevice = 0;
      deviceSpinner.setSelection(newSelectedDevice);
      channelSpinnerAdapter.clear();
    }

    // Hide the current fragment if the device list has become empty
    if (numberOfChannelsPerDevice.length == 0) {
      FragmentTransaction transaction = fragmentManager.beginTransaction();
      if (fragmentManager.findFragmentByTag("" + selectedDevice + selectedChannel) != null) {
        transaction.hide(fragmentManager.findFragmentByTag("" + selectedDevice + selectedChannel));
        transaction.commit();
      }
      channelSpinnerAdapter.clear();
    } else if (newSelectedDevice < numberOfChannelsPerDevice.length) {
      // Show the newly selected channel fragment
      if (newSelectedChannel < numberOfChannelsPerDevice[newSelectedDevice]
          && findViewById(R.id.fragment_container) != null) {
        if (fragmentManager.findFragmentByTag("" + selectedDevice + selectedChannel) != null) {
          if (fragmentManager.findFragmentByTag("" + newSelectedDevice + newSelectedChannel)
              != null) {
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction
                .hide(fragmentManager.findFragmentByTag("" + selectedDevice + selectedChannel));
            transaction.show(
                fragmentManager.findFragmentByTag("" + newSelectedDevice + newSelectedChannel));
            transaction.commit();
            selectedDevice = newSelectedDevice;
            selectedChannel = newSelectedChannel;
          }

          ((PrintoutFragment) fragmentManager
              .findFragmentByTag("" + selectedDevice + selectedChannel)).addMessages(
              uiData.getChannelMessages(selectedDevice, selectedChannel));
          if (busSwitch.isChecked() != uiData.getBusStatus(selectedDevice, selectedChannel)) {
            busSwitch.setChecked(uiData.getBusStatus(selectedDevice, selectedChannel));
          }
        }
      }

      // Populate the channel spinner
      if (channelSpinnerAdapter.getCount() != numberOfChannelsPerDevice[selectedDevice]
          && numberOfChannelsPerDevice[selectedDevice] != -1) {
        channelSpinnerAdapter.clear();
        for (int i = 0; i < numberOfChannelsPerDevice[selectedDevice]; i++) {
          channelSpinnerAdapter.add("CH" + (i + 1));
        }
      }
    }
  }

  private enum IdTypes {STANDARD, EXTENDED}

  /*
   * Moves keyboard selection/focus from byteField to nextByteField if byteField has length 2 after it changed
   */
  private class byteFieldTextWatcher implements TextWatcher {

    EditText byteField, nextByteField;

    public byteFieldTextWatcher(EditText byteField, EditText nextByteField) {
      this.byteField = byteField;
      this.nextByteField = nextByteField;
    }

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    public void afterTextChanged(Editable s) {
      if (byteField.getText().length() == 2) {
        nextByteField.requestFocus();
      }
    }
  }

  private class BusSwitchListener implements OnCheckedChangeListener {

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
      if (numberOfChannelsPerDevice != null) {
        if (selectedDevice < numberOfChannelsPerDevice.length) {
          if (selectedChannel < numberOfChannelsPerDevice[selectedDevice]) {
            if (isChecked) {
              busSwitch.setText("Bus on");
              incomingHandler.sendRequest(
                  Message
                      .obtain(null, CanLibService.MSG_SET_BUS_ON, selectedDevice, selectedChannel));
            } else {
              busSwitch.setText("Bus off");
              incomingHandler.sendRequest(
                  Message
                      .obtain(null, CanLibService.MSG_SET_BUS_OFF, selectedDevice,
                              selectedChannel));
            }
          }
        }
      } else {
        buttonView.setChecked(!isChecked);
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

        // Re-initialize device lists and fragment list list
        case CanLibService.MSG_NUMBER_OF_DEVICES:
          numberOfChannelsPerDevice = new int[msg.arg1];
          deviceInfo = new Bundle[msg.arg1];
          if (numberOfChannelsPerDevice.length == 0) {
            UpdateUI();
          } else {
            Message[] numMsg = new Message[numberOfChannelsPerDevice.length];
            Message[] infoMsg = new Message[numberOfChannelsPerDevice.length];
            // Ask about device info for each device
            for (int i = 0; i < numberOfChannelsPerDevice.length; i++) {
              numberOfChannelsPerDevice[i] = -1;
              numMsg[i] = Message.obtain(null, CanLibService.MSG_NUMBER_OF_CHANNELS, i, 0);
              sendRequest(numMsg[i]);
              infoMsg[i] = Message.obtain(null, CanLibService.MSG_DEVICE_INFO, i, 0);
              sendRequest(infoMsg[i]);
            }
          }
          break;

        // Populate device list and fragment list lists
        case CanLibService.MSG_NUMBER_OF_CHANNELS:
          if (numberOfChannelsPerDevice != null) {
            if (msg.arg1 < numberOfChannelsPerDevice.length) {
              for (int i = 0; i < msg.arg2; i++) {
                if (fragmentManager.findFragmentByTag("" + msg.arg1 + i) == null) {
                  FragmentTransaction transaction = fragmentManager.beginTransaction();
                  PrintoutFragment fragment = new PrintoutFragment();
                  transaction.add(R.id.fragment_container, fragment, "" + msg.arg1 + i);
                  transaction.hide(fragment);
                  transaction.commit();
                }
              }
              numberOfChannelsPerDevice[msg.arg1] = msg.arg2;
              UpdateUI();
            }
          }
          break;

        // Populate device info list
        case CanLibService.MSG_DEVICE_INFO:
          if (numberOfChannelsPerDevice != null) {
            if (msg.arg1 < numberOfChannelsPerDevice.length) {
              deviceInfo[msg.arg1] = msg.getData();
              UpdateUI();
            }
          }
          break;

        // Append incoming message to the corresponding fragment view
        case CanLibService.MSG_READ_MESSAGE:
          if (numberOfChannelsPerDevice != null) {
            if (msg.arg1 < numberOfChannelsPerDevice.length) {
              if (msg.arg2 < numberOfChannelsPerDevice[msg.arg1]) {
                if (msg.arg1 == selectedDevice && msg.arg2 == selectedChannel) {
                  PrintoutFragment fragment = ((PrintoutFragment) fragmentManager
                      .findFragmentByTag("" + selectedDevice + selectedChannel));
                  if (fragment != null) {
                    fragment.appendMessage((CanMessage) msg.obj);
                  }
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