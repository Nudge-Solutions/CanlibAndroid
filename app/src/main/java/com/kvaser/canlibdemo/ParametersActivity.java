package com.kvaser.canlibdemo;

import android.app.*;
import android.content.*;
import android.os.*;
import android.preference.*;
import android.widget.*;

import java.util.*;

/**
 * Performs the logic behind the UI view which enables CAN channel parameter changes.
 */
public class ParametersActivity extends PreferenceActivity {
  
  private static Context context;
  private static int[] numberOfChannelsPerDevice = new int[] {};
  private static String[] deviceShortName = new String[] {};
  private static String[] deviceLongName = new String[] {};
  private static Messenger mService = null;
  private static Boolean mIsBound = false;
  private static Messenger mMessenger;
  private static CheckBoxPreference applyToAllChannelsPref, filterByMask, filterByRange;
  private static ListPreference filterMode, filterCombinationMode, filteredIds;
  private static Preference standardBitmask, extendedBitmask;
  private static SharedPreferences.OnSharedPreferenceChangeListener sharedPreferenceChangeListener;
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
      
      UpdatePreferenceScreen();
    }
    
    public void onServiceDisconnected(ComponentName className) {
      mService = null;
    }
    
  };
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    context = this;
    
    PreferenceMenuFragment preferenceMenuFragment = new PreferenceMenuFragment();
    
    // Display the fragment as the main content.
    getFragmentManager().beginTransaction()
                        .replace(android.R.id.content, preferenceMenuFragment)
                        .commit();
    
    incomingHandler = new IncomingHandler();
    mMessenger = new Messenger(incomingHandler);
  }
  
  @Override
  protected void onResume() {
    super.onResume();
    bindService(new Intent(this, CanLibService.class), mConnection, Context.BIND_AUTO_CREATE);
  }
  
  @Override
  protected void onPause() {
    super.onPause();
    
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
  
  private void UpdatePreferenceScreen() {
    
    PreferenceMenuFragment preferenceMenuFragment = new PreferenceMenuFragment();
    
    // Display the fragment as the main content.
    getFragmentManager().popBackStack();
    getFragmentManager().beginTransaction()
                        .replace(android.R.id.content, preferenceMenuFragment)
                        .commit();
  }
  
  public static class PreferenceMenuFragment extends PreferenceFragment {
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      addPreferencesFromResource(R.xml.preference_menu_fragment);
      
      PreferenceScreen mainScreen = (PreferenceScreen) findPreference("screen");
      CheckBoxPreference enableVirtualDevice = (CheckBoxPreference) findPreference(getString(R.string.preference_enablevirtual));
      enableVirtualDevice.setOnPreferenceChangeListener(new ToggleVirtualChangeListener());

      if (numberOfChannelsPerDevice != null) {
        if (numberOfChannelsPerDevice.length == 0) {
          PreferenceCategory category = new PreferenceCategory(context);
          category.setTitle("No device found");
          mainScreen.addPreference(category);
        } else {
          for (int i = 0; i < numberOfChannelsPerDevice.length; i++) {
            PreferenceCategory category = new PreferenceCategory(context);
            category.setTitle("Device: " + deviceLongName[i]);
            mainScreen.addPreference(category);
            
            Preference identifyDevice = new Preference(context);
            identifyDevice.setOnPreferenceClickListener(new IdentifyDeviceClickListener(i));
            identifyDevice.setSummary("Identify device");
            mainScreen.addPreference(identifyDevice);
            
            for (int j = 0; j < numberOfChannelsPerDevice[i]; j++) {
              PreferenceScreen channelScreen =
                  getPreferenceManager().createPreferenceScreen(context);
              channelScreen.setTitle("Channel " + (j + 1));
              channelScreen.setOnPreferenceClickListener(
                  new PreferenceClickListener(i, j));
              mainScreen.addPreference(channelScreen);
            }
          }
        }
      }
    }

    private class ToggleVirtualChangeListener implements Preference.OnPreferenceChangeListener {

      private int deviceIndex;

      @Override
      public boolean onPreferenceChange(Preference preference, Object object) {

        try {
          Message toggleMsg = Message
              .obtain(null, CanLibService.MSG_TOGGLE_VIRTUAL_DEVICE);
          toggleMsg.replyTo = mMessenger;
          mService.send(toggleMsg);
        } catch (RemoteException e) {
          // Failed to send message to server. Print trace and remove fragment.
          e.printStackTrace();
          getFragmentManager().beginTransaction().remove(PreferenceMenuFragment.this).commit();
        }
        return true;
      }
    }

    private class PreferenceClickListener implements Preference.OnPreferenceClickListener {

      private int deviceIndex, channelIndex;

      public PreferenceClickListener(int deviceIndex, int channelIndex) {
        this.deviceIndex = deviceIndex;
        this.channelIndex = channelIndex;
      }

      @Override
      public boolean onPreferenceClick(Preference preference) {

        try {
          Message updatePrefMsg = Message
              .obtain(null, CanLibService.MSG_UPDATE_SHARED_PREFERENCES, deviceIndex, channelIndex);
          updatePrefMsg.replyTo = mMessenger;
          mService.send(updatePrefMsg);
        } catch (RemoteException e) {
          // Failed to send message to server. Print trace and remove fragment.
          e.printStackTrace();
          getFragmentManager().beginTransaction().remove(PreferenceMenuFragment.this).commit();
        }
        return false;
      }
    }

    private class IdentifyDeviceClickListener implements Preference.OnPreferenceClickListener {

      private int deviceIndex;

      public IdentifyDeviceClickListener(int deviceIndex) {
        this.deviceIndex = deviceIndex;
      }

      @Override
      public boolean onPreferenceClick(Preference preference) {

        try {
          Message identifyMsg = Message
              .obtain(null, CanLibService.MSG_FLASH_LEDS, deviceIndex, 0);
          identifyMsg.replyTo = mMessenger;
          mService.send(identifyMsg);
        } catch (RemoteException e) {
          // Failed to send message to server. Print trace and remove fragment.
          e.printStackTrace();
          getFragmentManager().beginTransaction().remove(PreferenceMenuFragment.this).commit();
        }
        return false;
      }
    }
  }
  
  /**
   * Shows a preferences view for channel settings.
   */
  public static class PrefFragment extends PreferenceFragment {
    
    private int deviceIndex, channelIndex;

    private EditTextPreference bitratePreference, tseg1Preference, tseg2Preference, sjwPreference;
    private ListPreference driverTypePreference;
    
    private void setupTimingSettings() {

      findPreference("pref_category")
          .setTitle("Settings for channel " + (channelIndex + 1) + " on device "
                    + deviceShortName[deviceIndex]);

      applyToAllChannelsPref = (CheckBoxPreference) findPreference(getString(
          R.string.preference_applytoall));
      applyToAllChannelsPref.setChecked(false);

      // Find preferences and set the summary texts
      bitratePreference = (EditTextPreference) findPreference(getString(
          R.string.preference_bitrate));
      bitratePreference.setSummary(bitratePreference.getText() + " b/s");

      tseg1Preference = (EditTextPreference) findPreference(getString(
          R.string.preference_tseg1));
      tseg1Preference.setSummary(tseg1Preference.getText() + " time quanta");

      tseg2Preference = (EditTextPreference) findPreference(getString(
          R.string.preference_tseg2));
      tseg2Preference.setSummary(tseg2Preference.getText() + " time quanta");

      sjwPreference = (EditTextPreference) findPreference(getString(
          R.string.preference_sjw));
      sjwPreference.setSummary(sjwPreference.getText());

      driverTypePreference = (ListPreference) findPreference(getString(
          R.string.preference_driver_type));
      setDriverTypeText(driverTypePreference, Integer.parseInt(driverTypePreference.getValue()));

      // Set listeners
      bitratePreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
          if (checkValue(Integer.parseInt(newValue.toString()), 1, 1000000)) {
            preference.setSummary(newValue.toString() + " b/s");
            return true;
          } else {
            return false;
          }
        }
      });
      tseg1Preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
          if (checkValue(Integer.parseInt(newValue.toString()), 1, 100)) {
            preference.setSummary(newValue.toString() + " time quanta");
            return true;
          } else {
            return false;
          }
        }
      });
      tseg2Preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
          if (checkValue(Integer.parseInt(newValue.toString()), 1, 100)) {
            preference.setSummary(newValue.toString() + " time quanta");
            return true;
          } else {
            return false;
          }
        }
      });
      sjwPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
          if (checkValue(Integer.parseInt(newValue.toString()), 1, 4)) {
            preference.setSummary(newValue.toString());
            return true;
          } else {
            return false;
          }
        }
      });
      driverTypePreference
          .setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
              applyToAllChannelsPref.setChecked(false);
              setDriverTypeText((ListPreference) preference, Integer.parseInt(newValue.toString()));
              return true;
            }
          });

      applyToAllChannelsPref
          .setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
              applyToAllChannelsPref.setChecked(false);

              // Show a dialog when apply to all channels is pressed
              new AlertDialog.Builder(context)
                  .setTitle("Apply settings?")
                  .setMessage("Do you really want to apply settings to all channels?")
                  .setIcon(android.R.drawable.ic_dialog_alert)
                  .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int whichButton) {
                      // Tell service to update parameters
                      try {
                        Message updatePrefMsg =
                            Message.obtain(null, CanLibService.MSG_WRITE_PARAMETERS_ALL_CHANNELS);
                        updatePrefMsg.replyTo = mMessenger;
                        mService.send(updatePrefMsg);
                      } catch (RemoteException e) {
                        // Failed to send message to server. Print trace and remove fragment.
                        e.printStackTrace();
                        getFragmentManager().beginTransaction().remove(PrefFragment.this).commit();
                      }
                      Toast
                          .makeText(context, "Settings applied to all channels", Toast.LENGTH_SHORT)
                          .show();
                      applyToAllChannelsPref.setChecked(true);
                    }
                  })
                  .setNegativeButton(android.R.string.no, null).show();

              // Return false so that the system does not overwrite checkbox
              return false;
            }
          });
    }

    private void setupFilterSettings() {

      findPreference("filter_category")
          .setTitle("Filter settings for channel " + (channelIndex + 1) + " on device "
                    + deviceShortName[deviceIndex]);

      EditTextPreference filterMask, filterCode, filterMin, filterMax;

      filterMask = (EditTextPreference) findPreference(getString(R.string.preference_filter_mask));
      filterCode = (EditTextPreference) findPreference(getString(R.string.preference_filter_code));
      filterMin = (EditTextPreference) findPreference(getString(R.string.preference_filter_min_id));
      filterMax = (EditTextPreference) findPreference(getString(R.string.preference_filter_max_id));
      standardBitmask = findPreference(getString(R.string.preference_filter_std_bitmask));
      extendedBitmask = findPreference(getString(R.string.preference_filter_ext_bitmask));
      filterByMask =
          (CheckBoxPreference) findPreference(getString(R.string.preference_filter_by_mask));
      filterByRange =
          (CheckBoxPreference) findPreference(getString(R.string.preference_filter_by_range));
      filterMode = (ListPreference) findPreference(getString(R.string.preference_filter_mode));
      filterCombinationMode =
          (ListPreference) findPreference(getString(R.string.preference_filter_combination_mode));
      filteredIds =
          (ListPreference) findPreference(getString(R.string.preference_filtered_ids));

      filterMode.setEnabled(filterByMask.isChecked() || filterByRange.isChecked());
      filterCombinationMode.setEnabled(filterByMask.isChecked() && filterByRange.isChecked());
      filteredIds.setEnabled(filterByMask.isChecked() || filterByRange.isChecked());

      filterMask.setSummary(filterMask.getText());
      filterCode.setSummary(filterCode.getText());
      filterMin.setSummary(filterMin.getText());
      filterMax.setSummary(filterMax.getText());
      SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
      standardBitmask.setSummary(
          preferences.getString(getString(R.string.preference_filter_std_bitmask), "0"));
      extendedBitmask.setSummary(preferences.getString(
          getString(R.string.preference_filter_ext_bitmask), "0"));

      filterMode.setSummary(filterMode.getEntry());
      filterCombinationMode.setSummary(filterCombinationMode.getEntry());
      filteredIds.setSummary(filteredIds.getEntry());

      // Set listeners
      Preference.OnPreferenceChangeListener setSummaryListener =
          new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
              preference.setSummary(newValue.toString());
              return true;
            }
          };
      filterMask.setOnPreferenceChangeListener(setSummaryListener);
      filterCode.setOnPreferenceChangeListener(setSummaryListener);
      filterMin.setOnPreferenceChangeListener(setSummaryListener);
      filterMax.setOnPreferenceChangeListener(setSummaryListener);

      Preference.OnPreferenceChangeListener setListSummaryListener =
          new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
              ((ListPreference) preference).setValue(newValue.toString());
              ((ListPreference) preference).setSummary(((ListPreference) preference).getEntry());
              return false;
            }
          };
      filterMode.setOnPreferenceChangeListener(setListSummaryListener);
      filterCombinationMode.setOnPreferenceChangeListener(setListSummaryListener);
      filteredIds.setOnPreferenceChangeListener(setListSummaryListener);

      filterByMask.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
          filterMode.setEnabled((boolean) newValue || filterByRange.isChecked());
          filterCombinationMode.setEnabled((boolean) newValue && filterByRange.isChecked());
          filteredIds.setEnabled((boolean) newValue || filterByRange.isChecked());
          return true;
        }
      });
      filterByRange.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
          filterMode.setEnabled((boolean) newValue || filterByMask.isChecked());
          filterCombinationMode.setEnabled((boolean) newValue && filterByMask.isChecked());
          filteredIds.setEnabled((boolean) newValue || filterByRange.isChecked());
          return true;
        }
      });
    }

    private Boolean checkValue(int val, int min, int max) {
      if ((val >= min) && (val <= max)) {
        applyToAllChannelsPref.setChecked(false);
        return true;
      } else {
        Toast.makeText(context, "Invalid value. Please enter a value between " + min +
                                " and " + max + ".", Toast.LENGTH_LONG).show();
        return false;
      }
    }

    private void setDriverTypeText(ListPreference preference, int value) {
      switch (value) {
        case 0:
          preference.setSummary("Driver type: Normal");
          break;
        case 1:
          preference.setSummary("Driver type: Off");
          break;
        case 2:
          preference.setSummary("Driver type: Silent");
          break;
        case 3:
          preference.setSummary("Driver type: Self reception");
          break;
      }
    }

    public void onResume() {
      super.onResume();
      sharedPreferenceChangeListener =
          new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
              switch (key) {
                case "preference_filter_std_bitmask":
                  standardBitmask.setSummary(sharedPreferences.getString(
                      getString(R.string.preference_filter_std_bitmask), "0"));
                  break;
                case "preference_filter_ext_bitmask":
                  extendedBitmask.setSummary(sharedPreferences.getString(
                      getString(R.string.preference_filter_ext_bitmask), "0"));
                  break;
                default:
                  break;
              }
            }
          };

      PreferenceManager.getDefaultSharedPreferences(context)
                       .registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
    }

    @Override
    public void onPause() {
      super.onPause();

      // Tell service to update parameters
      try {
        Message updatePrefMsg = Message.obtain(null, CanLibService.MSG_WRITE_PARAMETERS);
        updatePrefMsg.replyTo = mMessenger;
        updatePrefMsg.arg1 = deviceIndex;
        updatePrefMsg.arg2 = channelIndex;
        mService.send(updatePrefMsg);
      } catch (RemoteException e) {
        // Failed to send message to server. Print trace.
        e.printStackTrace();
      }

      PreferenceManager.getDefaultSharedPreferences(context)
                       .unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      addPreferencesFromResource(R.xml.preference_fragment);

      Bundle argBundle = getArguments();
      deviceIndex = argBundle.getInt("deviceIndex");
      channelIndex = argBundle.getInt("channelIndex");

      SharedPreferences.Editor editor =
          PreferenceManager.getDefaultSharedPreferences(context).edit();
      editor.putInt(getString(R.string.selected_device), deviceIndex);
      editor.putInt(getString(R.string.selected_channel), channelIndex);
      editor.commit();

      setupTimingSettings();
      setupFilterSettings();
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
          int numDevices = msg.arg1;
          numberOfChannelsPerDevice = new int[numDevices];
          deviceShortName = new String[numDevices];
          deviceLongName = new String[numDevices];
          
          if (numDevices == 0) {
            UpdatePreferenceScreen();
          } else {
            Message[] deviceInfoMsg = new Message[numDevices];
            Message[] numMsg = new Message[numDevices];
            // Ask about device info for each device
            for (int i = 0; i < numDevices; i++) {
              numberOfChannelsPerDevice[i] = -1;
              deviceShortName[i] = "";
              deviceLongName[i] = "";
              deviceInfoMsg[i] = Message.obtain(null, CanLibService.MSG_DEVICE_INFO, i, 0);
              sendRequest(deviceInfoMsg[i]);
              numMsg[i] = Message.obtain(null, CanLibService.MSG_NUMBER_OF_CHANNELS, i, 0);
              sendRequest(numMsg[i]);
            }
          }
          break;
        
        // Populate device list and fragment list lists
        case CanLibService.MSG_DEVICE_INFO:
          if ((deviceShortName != null) && (deviceLongName != null)) {
            if ((msg.arg1 < deviceShortName.length) && ((msg.arg1 < deviceLongName.length))) {
              Bundle deviceInfo = msg.getData();
              String serial = deviceInfo.getString("Serial Number");
              String deviceName = deviceInfo.getString("Device Name", "" + msg.arg1);
              if (serial != null) {
                deviceShortName[msg.arg1] = "Serial " + deviceInfo.getString("Serial Number");
                deviceLongName[msg.arg1] =
                    deviceInfo.getString("Device Name", "" + msg.arg1) + ", Serial " + deviceInfo
                        .getString("Serial Number");
              } else {
                deviceShortName[msg.arg1] = "" + msg.arg1;
                deviceLongName[msg.arg1] =
                    deviceInfo.getString("Device Name", "" + msg.arg1) + " (" + msg.arg1 + ")";
              }
              UpdatePreferenceScreen();
            }
          }
          break;
        
        // Populate device list and fragment list lists
        case CanLibService.MSG_NUMBER_OF_CHANNELS:
          if (numberOfChannelsPerDevice != null) {
            if (msg.arg1 < numberOfChannelsPerDevice.length) {
              //for (int i = 0; i < msg.arg2; i++) {
              // For each channel. Anything to do here?
              //}
              numberOfChannelsPerDevice[msg.arg1] = msg.arg2;
              UpdatePreferenceScreen();
            }
          }
          break;
        
        // Get ACK of updated shared preferences for the specified channel
        case CanLibService.MSG_UPDATE_SHARED_PREFERENCES:
          if (msg.arg1 < numberOfChannelsPerDevice.length) {
            if (msg.arg2 < numberOfChannelsPerDevice[msg.arg1]) {
              
              // Create the settings view for the channel
              PrefFragment prefFragment = new PrefFragment();
              Bundle argBundle = new Bundle();
              argBundle.putInt("deviceIndex", msg.arg1);
              argBundle.putInt("channelIndex", msg.arg2);
              prefFragment.setArguments(argBundle);
              
              // Display the fragment as the main content.
              getFragmentManager().beginTransaction()
                                  .addToBackStack("Settings")
                                  .replace(android.R.id.content, prefFragment)
                                  .commit();
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
