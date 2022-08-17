package com.kvaser.canlibdemo;

import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.os.*;
import android.support.v4.app.*;
import android.support.v7.app.*;
import android.text.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.AdapterView.*;
import com.kvaser.canlib.*;

import java.util.*;

/**
 * Presents a simple CAN log of all CAN traffic. The global message filter affects which messages
 * that are shown in the log.
 */
public class LogActivity extends AppCompatActivity {

  private final static int MAX_LINES = 1000;

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
  private boolean scrollAutomatically = true;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_log);

    android.support.v7.widget.Toolbar toolbar =
        (android.support.v7.widget.Toolbar) findViewById(R.id.main_toolbar);

    setSupportActionBar(toolbar);

    DisplayMetrics displayMetrics = this.getResources().getDisplayMetrics();
    float dpWidth = displayMetrics.widthPixels / displayMetrics.density;
    if (dpWidth < 570) {
      // Log will not fit in this mode - go to landscape
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    uiData = (UiData) getApplicationContext();

    UpdateUI();

    incomingHandler = new IncomingHandler();
    mMessenger = new Messenger(incomingHandler);
  }
  
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.toolbar_menu, menu);
    MenuItem clearLogItem = menu.findItem(R.id.clear_log);
    MenuItem autoScrollItem = menu.findItem(R.id.auto_scroll);
    autoScrollItem.setActionView(R.layout.scroll_switch);
    Switch scrollSwitch = (Switch) autoScrollItem.getActionView().findViewById(R.id.scroll_switch);
    scrollSwitch.setChecked(scrollAutomatically);
    scrollSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        scrollAutomatically = isChecked;
      }
    });
    clearLogItem.setVisible(true);
    autoScrollItem.setVisible(true);
    return true;
  }
  
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Start parameters activity if settings button was selected
    switch (item.getItemId()) {
      case R.id.action_settings:
        Intent intent = new Intent(this, ParametersActivity.class);
        startActivity(intent);
        return true;
      case R.id.clear_log:
        uiData.clearLogMessages();
        UpdateUI();
        return true;
      default:
        break;
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
    mIsBound = true;
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
    ((TextView) findViewById(R.id.log_data)).setText("");
    addMessages(uiData.getLogMessages());
  }
  
  private void addMessages(UiData.LogMessage[] messages) {
    if (messages != null) {
      for (UiData.LogMessage message : messages) {
        if (message.canMessage != null) {
          appendLogData(message.deviceIndex, message.channelIndex, message.canMessage);
        }
        if (message.chipState != null) {
          appendLogData(message.deviceIndex, message.channelIndex, message.chipState);
        }
      }
    }
  }

  private void scrollDown() {
    final ScrollView scrollView = (ScrollView) findViewById(R.id.scroll_log);
    scrollView.post(new Runnable() {
      public void run() {
        scrollView.fullScroll(View.FOCUS_DOWN);
      }
    });
  }

  private void appendLogData(int deviceIndex, int channelIndex, CanMessage msg) {
    TextView logData = (TextView) findViewById(R.id.log_data);
    if ((msg != null) && (logData != null)) {
      String device = prePad(Integer.toString(deviceIndex), 2) + " ";
      String channel = prePad(Integer.toString(channelIndex + 1), 3);
      String idExtendedSuffix = (msg.isFlagSet(CanMessage.MessageFlags.EXTENDED_ID) ? "x" : "");
      String id = "";
      String dlc = "";
      String data = "";
      int dataLen = 8;
      if (msg.isFlagSet(CanMessage.MessageFlags.ERROR_FRAME)) {
        id = prePad("0" + idExtendedSuffix, 10);
        dlc = prePad("", 4);
        data = " ErrorFrame             ";
      } else {
        id = prePad(Long.toHexString(msg.id).toUpperCase() + idExtendedSuffix, 10);
        dlc = prePad(Integer.toString(msg.dlc), 4);
        dataLen = Math.min(msg.dlc, 8);
        for (int i = 0; i < dataLen; i++) {
          data = data + prePad(
              prePad(Integer.toHexString(((int) msg.data[i]) & 0xff).toUpperCase(), "0", 2), 3);
        }
      }
      Long t = msg.getTimestamp();
      String time = prePad(Long.toString(t / 100000) + "." + prePad(Long.toString(t % 100000), "0",
                                                                    5), 13 + ((8 - dataLen) * 3));
      String dir = (msg.getDirection() == CanMessage.Direction.RX) ? "  Rx" : "  Tx";

      logData.append(device + channel + id + dlc + data + time + dir + '\n');
      eraseExcessiveLines();

      if (scrollAutomatically) {
        scrollDown();
      }
    }
  }
  
  private void appendLogData(int deviceIndex, int channelIndex, ChipState chipState) {
    TextView logData = (TextView) findViewById(R.id.log_data);
    if ((chipState != null) && (logData != null)) {
      for (ChipState.BusStatus busState : chipState.getBusStatus()) {
        String device = prePad(Integer.toString(deviceIndex), 2) + " ";
        String channel = prePad(Integer.toString(channelIndex + 1), 3);
        String state = prePad(postPad(busState.toString(), 15), 19);
        String tec = postPad("TEC:" + chipState.getTxErrorCounter(), 9);
        String rec = postPad("REC:" + chipState.getRxErrorCounter(), 9);
        Long t = chipState.getTimeStamp();
        String time = prePad(
            Long.toString(t / 100000) + "." + prePad(Long.toString(t % 100000), "0", 5), 14);
        String color = "";
        switch (busState) {
          case ERROR_ACTIVE:
            color = "#008800";
            break;
          case ERROR_WARNING:
            color = "#FF8800";
            break;
          case ERROR_PASSIVE:
          case BUSOFF:
            color = "#FF0000";
            break;
          default:
            color = "#000000";
            break;
        }
        String fullEntry = device + channel + state + tec + rec + time;
        fullEntry = fullEntry.replace(" ", "&nbsp;");
        logData.append(Html.fromHtml("<font color='" + color + "'>" + fullEntry + "</font>"));
        logData.append("" + '\n');
        eraseExcessiveLines();
      }
    }
  }

  private void eraseExcessiveLines() {
    TextView logData = (TextView) findViewById(R.id.log_data);
    int excessLineNumber = logData.getLineCount() - MAX_LINES;
    if (excessLineNumber > 0) {
      int eolIndex = -1;
      CharSequence charSequence = logData.getText();
      for (int i = 0; i < excessLineNumber; i++) {
        do {
          eolIndex++;
        } while (eolIndex < charSequence.length() && charSequence.charAt(eolIndex) != '\n');
      }
      if (eolIndex < charSequence.length()) {
        logData.getEditableText().delete(0, eolIndex + 1);
      } else {
        logData.setText("");
      }
    }
  }
  
  private String postPad(String text, int wantedLength) {
    int origLen = text.length();
    for (int i = 0; i < (wantedLength - origLen); i++) {
      text = text + " ";
    }
    return text;
  }
  
  private String prePad(String text, int wantedLength) {
    return prePad(text, " ", wantedLength);
  }
  
  private String prePad(String text, String padChar, int wantedLength) {
    int origLen = text.length();
    for (int i = 0; i < (wantedLength - origLen); i++) {
      text = padChar + text;
    }
    return text;
  }
  
  /*
   * Handler of incoming messages from service.
   */
  class IncomingHandler extends Handler {
    
    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        
        case CanLibService.MSG_REGISTER_CLIENT:
          break;

        // Append incoming message to the log
        case CanLibService.MSG_READ_MESSAGE:
          appendLogData(msg.arg1, msg.arg2, (CanMessage) msg.obj);
          break;

        // Append incoming chip state event to the log
        case CanLibService.MSG_CHIP_STATE:
          appendLogData(msg.arg1, msg.arg2, (ChipState) msg.obj);
          break;

        default:
          super.handleMessage(msg);
          break;
      }
    }
  }
}