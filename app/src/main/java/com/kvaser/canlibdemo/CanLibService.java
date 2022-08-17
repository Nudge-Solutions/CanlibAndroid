package com.kvaser.canlibdemo;

import android.app.*;
import android.content.*;
import android.os.*;
import android.preference.*;
import android.support.v4.util.*;

import java.util.*;

import com.kvaser.canlib.*;

/**
 * Main background service which provides an interfaces between the UI and CanLib. The service
 * should be started when the app starts to enable all functionality.
 *
 * The service maintains a list of devices and channels. All channels of all devices are opened and
 * put in the list when a Kvaser USB device connects or disconnects. A MSG_NUMBER_OF_DEVICES
 * message is broadcast on such an event to enable UI components to update.
 */
public class CanLibService extends Service {

  // Messages supported by the service
  static final int MSG_REGISTER_CLIENT = 0;
  static final int MSG_UNREGISTER_CLIENT = 1;
  static final int MSG_NUMBER_OF_CHANNELS = 3;
  static final int MSG_SEND_MESSAGE = 4;
  static final int MSG_READ_MESSAGE = 5;
  static final int MSG_WRITE_PARAMETERS = 6;
  static final int MSG_WRITE_PARAMETERS_ALL_CHANNELS = 7;
  static final int MSG_SET_BUS_ON = 8;
  static final int MSG_SET_BUS_OFF = 9;
  static final int MSG_DEVICE_INFO = 10;
  static final int MSG_NUMBER_OF_DEVICES = 11;
  static final int MSG_FLASH_LEDS = 12;
  static final int MSG_READ_PARAMETERS = 13;
  static final int MSG_UPDATE_SHARED_PREFERENCES = 14;
  static final int MSG_CHIP_STATE = 15;
  static final int MSG_TOGGLE_VIRTUAL_DEVICE = 16;
  
  //Error messages
  static final int ERROR_ILLEGAL_ARGUMENT = -1;
  
  private static CanLib canlib;
  private static int preferencesDeviceIndex, preferencesChannelIndex;
  // List keeping track of all currently registered clients
  private ArrayList<Messenger> mClients = new ArrayList<Messenger>();
  private BackgroundThread backgroundThread;
  private Thread pollThread;
  // Messenger used by clients to send messages to the service
  private Messenger mMessenger;
  private IncomingHandler incomingHandler;
  // List of connected devices
  private List<KvDevice> deviceList = new ArrayList<>();
  // Map of connected devices to lists of their channels
  private SimpleArrayMap<KvDevice, List<KvChannel>> channelMap = new SimpleArrayMap<>();
  // Map of channels to their listenerss
  private SimpleArrayMap<KvChannel, ChannelListener> listenerMap = new SimpleArrayMap<>();
  private UiData uiData;
  private SharedPreferenceChangeListener sharedPreferenceChangeListener;
  
  /**
   * Initializes the service and starts the main background threads.
   */
  @Override
  public void onCreate() {
    
    uiData = (UiData) getApplicationContext();
    
    sharedPreferenceChangeListener = new SharedPreferenceChangeListener();
    PreferenceManager.getDefaultSharedPreferences(this)
                     .registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
    
    // Spawn a thread to perform initialization
    if (backgroundThread == null) {
      backgroundThread = new BackgroundThread();
      backgroundThread.start();
    }
    
    // Spawn a thread to poll CanLib for device updates
    pollThread = new Thread(new Runnable() {
      @Override
      public void run() {
        while (!pollThread.isInterrupted()) {
          try {
            Thread.sleep(1000);
            if (canlib != null) {
              // Update the device list when the number of connected devices has changed
              if (canlib.getNumberOfDevices() != deviceList.size()) {
                updateDeviceList();
              }
            }
          } catch (InterruptedException e) {
            // Stop thread if interrupted
          }
        }
      }
    });
    pollThread.start();
  }
  
  /**
   * Interrupts the polling thread
   */
  @Override
  public void onDestroy() {
    PreferenceManager.getDefaultSharedPreferences(this)
                     .unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
    pollThread.interrupt();
  }
  
  @Override
  public IBinder onBind(Intent intent) {
    return mMessenger.getBinder();
  }
  
  /*
   * Loop through CanLib's currently connected devices and check if any should be added or removed
   * from the deviceList. Send a MSG_NUMBER_OF_DEVICES message to broadcast that the deviceList changed.
   */
  private void updateDeviceList() {
    
    KvDevice tempDevice;
    
    // Map for finding devices to remove from deviceList
    boolean[] validMap = new boolean[deviceList.size()];
    for (int l = 0; l < validMap.length; l++) {
      validMap[l] = false;
    }
    
    // Loop through CanLib's currently connected devices
    for (int i = 0; i < canlib.getNumberOfDevices(); i++) {
      tempDevice = canlib.getDevice(i);
      boolean isNew = true;
      // Check if the device already was in the deviceList
      for (KvDevice device : deviceList) {
        if (deviceList.indexOf(device) < validMap.length) {
          if (device.getEan().equals(tempDevice.getEan()) && (device.getSerialNumber() ==
                                                              tempDevice.getSerialNumber())) {
            isNew = false;
            validMap[deviceList.indexOf(device)] = true;
          }
        }
      }
      // Add the device if it was not in the list
      if (isNew) {
        deviceList.add(tempDevice);
        channelMap.put(tempDevice, new ArrayList<KvChannel>());
        uiData.addDevice();
        uiData
            .setNumberOfChannels(deviceList.indexOf(tempDevice), tempDevice.getNumberOfChannels());
        
        // Open channels and add them to channel list
        for (int k = 0; k < tempDevice.getNumberOfChannels(); k++) {
          try {
            channelMap.get(tempDevice).add(tempDevice.openChannel(k, EnumSet.of(
                KvChannel.ChannelFlags.ACCEPT_LARGE_DLC)));
            ChannelListener listener =
                new ChannelListener(tempDevice, k);
            channelMap.get(tempDevice).get(k).registerCanMessageListener(listener);
            listenerMap.put(channelMap.get(tempDevice).get(k), listener);
            // Write stored parameters to the new channel
            writeParameters(deviceList.indexOf(tempDevice), k, false);
            BusStateListener stateListener =
                new BusStateListener(deviceList.indexOf(tempDevice), k);
            channelMap.get(tempDevice).get(k).registerChipStateListener(stateListener);
          } catch (CanLibException e) {
            // Print trace and try to open the next channel
            e.printStackTrace();
          }
        }
      }
    }
    
    // Remove the devices which was not found in CanLib
    for (int j = validMap.length - 1; j >= 0; j--) {
      if (!validMap[j]) {
        for (KvChannel channel : channelMap.get(deviceList.get(j))) {
          channel.unregisterCanMessageListener(listenerMap.get(channel));
          listenerMap.remove(channel);
        }
        channelMap.remove(deviceList.get(j));
        deviceList.remove(j);
        uiData.removeDevice(j);
      }
    }

    // Broadcast that the device list has changed
    incomingHandler.broadcast(Message.obtain(null, MSG_NUMBER_OF_DEVICES, deviceList.size(), 0));
  }
  
  private void writeParameters(int deviceIndex, int channelIndex,
                               boolean updateAllDevicesAndChannels) throws CanLibException {
    CanBusParams parameters = new CanBusParams();
    // Get the preferences from the shared preferences manager
    SharedPreferences preferences =
        PreferenceManager.getDefaultSharedPreferences(CanLibService.this);
    parameters.bitRate = Integer.parseInt(
        preferences.getString(getString(R.string.preference_bitrate), "125000"));
    parameters.tseg1 = Integer
        .parseInt(preferences.getString(getString(R.string.preference_tseg1), "11"));
    parameters.tseg2 = Integer
        .parseInt(preferences.getString(getString(R.string.preference_tseg2), "4"));
    parameters.sjw =
        Integer
            .parseInt(preferences.getString(getString(R.string.preference_sjw), "1"));
    
    CanDriverType type = CanDriverType.values()[Integer.parseInt(
        preferences.getString(getString(R.string.preference_driver_type), "0"))];
    
    if (updateAllDevicesAndChannels) {
      // Apply settings to all channels on all devices
      for (KvDevice device : deviceList) {
        for (int i = 0; i < channelMap.get(device).size(); i++) {
          channelMap.get(device).get(i).setBusParams(parameters);
          channelMap.get(device).get(i).setBusOutputControl(type);
        }
      }
    } else {
      channelMap.get(deviceList.get(deviceIndex)).get(channelIndex).setBusParams(parameters);
      channelMap.get(deviceList.get(deviceIndex)).get(channelIndex).setBusOutputControl(type);
    }
  }
  
  /*
   * Reads channel bus settings and filter settings and stores them in the shared preferences
   */
  private void readParameters(int deviceIndex, int channelIndex) {
    
    preferencesDeviceIndex = deviceIndex;
    preferencesChannelIndex = channelIndex;
    
    // Get the preferences from the shared preferences manager
    SharedPreferences preferences =
        PreferenceManager.getDefaultSharedPreferences(CanLibService.this);
    
    try {
      
      CanBusParams parameters =
          channelMap.get(deviceList.get(deviceIndex)).get(channelIndex).getBusParams();
      
      CanDriverType type;
      try {
        type = channelMap.get(deviceList.get(deviceIndex)).get(
            channelIndex).getBusOutputControl();
      } catch (CanLibException e) {
        // getBusOutputControl may throw exception for old firmwares that does not support this
        // request, this is not fatal and we should continue filling in settings
        type = null;
      }
      
      if (parameters != null) {// && type != null) {
        
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(getString(R.string.preference_bitrate), Long.toString(parameters.bitRate));
        editor.putString(getString(R.string.preference_tseg1), Integer.toString(parameters.tseg1));
        editor.putString(getString(R.string.preference_tseg2), Integer.toString(parameters.tseg2));
        editor.putString(getString(R.string.preference_sjw), Integer.toString(parameters.sjw));
        if (type != null) {
          editor.putString(getString(R.string.preference_driver_type),
                           Integer.toString(type.ordinal()));
        }
        editor.commit();
      }
    } catch (CanLibException e) {
      e.printStackTrace();
    }
    
    CanMessageFilter filter =
        listenerMap.get(channelMap.get(deviceList.get(deviceIndex)).get(channelIndex))
            .messageFilter;

    SharedPreferences.Editor editor = preferences.edit();
    if (filter != null) {

      CanMessageFilter.FilterType mode = filter.filterType;
      if (mode == CanMessageFilter.FilterType.PASS) {
        editor.putString(getString(R.string.preference_filter_mode), "0");
      } else {
        editor.putString(getString(R.string.preference_filter_mode), "1");
      }

      CanMessageFilter.FilterMatchType combinationMode = filter.filterMatchType;
      switch (combinationMode) {
        case MASK:
          editor.putBoolean(getString(R.string.preference_filter_by_mask), true);
          editor.putBoolean(getString(R.string.preference_filter_by_range), false);
          break;
        case RANGE:
          editor.putBoolean(getString(R.string.preference_filter_by_mask), false);
          editor.putBoolean(getString(R.string.preference_filter_by_range), true);
          break;
        case MASK_AND_RANGE:
          editor.putString(getString(R.string.preference_filter_combination_mode), "0");
          editor.putBoolean(getString(R.string.preference_filter_by_mask), true);
          editor.putBoolean(getString(R.string.preference_filter_by_range), true);
          break;
        case MASK_OR_RANGE:
          editor.putString(getString(R.string.preference_filter_combination_mode), "1");
          editor.putBoolean(getString(R.string.preference_filter_by_mask), true);
          editor.putBoolean(getString(R.string.preference_filter_by_range), true);
          break;
        default:
          break;
      }

      CanMessageFilter.FilterIdType filteredIds = filter.filterIdType;
      if (filteredIds == CanMessageFilter.FilterIdType.STANDARD) {
        editor.putString(getString(R.string.preference_filtered_ids), "0");
      } else if (filteredIds == CanMessageFilter.FilterIdType.EXTENDED) {
        editor.putString(getString(R.string.preference_filtered_ids), "1");
      } else {
        editor.putString(getString(R.string.preference_filtered_ids), "2");
      }

      editor.putString(getString(R.string.preference_filter_mask),
                       Integer.toHexString(filter.mask).toUpperCase());
      editor.putString(getString(R.string.preference_filter_code),
                       Integer.toHexString(filter.code).toUpperCase());
      editor
          .putString(getString(R.string.preference_filter_min_id),
                     Integer.toHexString(filter.idMin).toUpperCase());
      editor
          .putString(getString(R.string.preference_filter_max_id),
                     Integer.toHexString(filter.idMax).toUpperCase());
      editor.putString(getString(R.string.preference_filter_std_bitmask),
                       filter.getBitMaskStandard());
      editor.putString(getString(R.string.preference_filter_ext_bitmask),
                       filter.getBitMaskExtended());

    } else {
      editor.putBoolean(getString(R.string.preference_filter_by_mask), false);
      editor.putBoolean(getString(R.string.preference_filter_by_range), false);
    }
    editor.commit();
  }
  
  /*
   * Updates channel filter when a filter preference has changed
   */
  private class SharedPreferenceChangeListener implements SharedPreferences.OnSharedPreferenceChangeListener {
    
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

      if (preferencesDeviceIndex >= deviceList.size()) {
        return;
      }

      CanMessageFilter filter =
          listenerMap.get(
              channelMap.get(deviceList.get(preferencesDeviceIndex)).get(preferencesChannelIndex))
              .messageFilter;
      
      switch (key) {
        case "preference_filter_combination_mode":
        case "preference_filter_by_mask":
        case "preference_filter_by_range":
          boolean filterByMask =
              sharedPreferences.getBoolean(getString(R.string.preference_filter_by_mask), false);
          boolean filterByRange =
              sharedPreferences.getBoolean(getString(R.string.preference_filter_by_range), false);
          String filterCombinationMode = sharedPreferences.getString(
              getString(R.string.preference_filter_combination_mode), "0");

          if (filterByMask || filterByRange) {
            if (filter == null) {
              filter = new CanMessageFilter();
              channelMap.get(deviceList.get(preferencesDeviceIndex)).get(preferencesChannelIndex)
                        .addFilter(filter);
              listenerMap.get(
                  channelMap.get(deviceList.get(preferencesDeviceIndex))
                            .get(preferencesChannelIndex)).messageFilter = filter;
            }
          } else if (filter != null) {
            channelMap.get(deviceList.get(preferencesDeviceIndex)).get(preferencesChannelIndex)
                      .removeFilter(filter);
            listenerMap.get(channelMap.get(deviceList.get(preferencesDeviceIndex))
                                      .get(preferencesChannelIndex)).messageFilter = null;
            filter = null;
          }

          if (filter != null) {
            if (filterByMask && filterByRange && filterCombinationMode.equals("0")) {
              filter.filterMatchType = CanMessageFilter.FilterMatchType.MASK_AND_RANGE;
            }
            if (filterByMask && filterByRange && filterCombinationMode.equals("1")) {
              filter.filterMatchType = CanMessageFilter.FilterMatchType.MASK_OR_RANGE;
            }
            if (filterByMask && !filterByRange) {
              filter.filterMatchType = CanMessageFilter.FilterMatchType.MASK;
            }
            if (!filterByMask && filterByRange) {
              filter.filterMatchType = CanMessageFilter.FilterMatchType.RANGE;
            }
          }
          break;
        case "preference_filter_mask":
          if (filter != null) {
            filter.mask = (int) Long.parseLong(
                sharedPreferences.getString(getString(R.string.preference_filter_mask), "0"), 16);
            sharedPreferences.edit()
                             .putString(getString(R.string.preference_filter_std_bitmask),
                                        filter.getBitMaskStandard())
                             .putString(getString(R.string.preference_filter_ext_bitmask),
                                        filter.getBitMaskExtended())
                             .apply();
          }
          break;
        case "preference_filter_code":
          if (filter != null) {
            filter.code = (int) Long.parseLong(
                sharedPreferences.getString(getString(R.string.preference_filter_code), "0"), 16);
            sharedPreferences.edit()
                             .putString(getString(R.string.preference_filter_std_bitmask),
                                        filter.getBitMaskStandard())
                             .putString(getString(R.string.preference_filter_ext_bitmask),
                                        filter.getBitMaskExtended())
                             .apply();
          }
          break;
        case "preference_filter_min_id":
          if (filter != null) {
            filter.idMin = (int) Long.parseLong(
                sharedPreferences.getString(getString(R.string.preference_filter_min_id), "0"),
                16);
          }
          break;
        case "preference_filter_max_id":
          if (filter != null) {
            filter.idMax = (int) Long.parseLong(
                sharedPreferences.getString(getString(R.string.preference_filter_max_id), "0"),
                16);
          }
          break;
        case "preference_filter_mode":
          if (filter != null) {
            String mode =
                sharedPreferences.getString(getString(R.string.preference_filter_mode), "0");
            if (mode.equals("1")) {
              filter.filterType = CanMessageFilter.FilterType.STOP;
            } else {
              filter.filterType = CanMessageFilter.FilterType.PASS;
            }
          }
          break;
        case "preference_filtered_ids":
          if (filter != null) {
            String mode =
                sharedPreferences.getString(getString(R.string.preference_filtered_ids), "0");
            if (mode.equals("0")) {
              filter.filterIdType = CanMessageFilter.FilterIdType.STANDARD;
            } else if (mode.equals("1")) {
              filter.filterIdType = CanMessageFilter.FilterIdType.EXTENDED;
            } else {
              filter.filterIdType = CanMessageFilter.FilterIdType.BOTH;
            }
          }
          break;
        default:
          break;
      }
    }
  }

  private class ChannelListener implements CanMessageListener {

    public CanMessageFilter messageFilter;
    private int channelIndex;
    private KvDevice device;

    public ChannelListener(KvDevice device, int channelIndex) {
      this.device = device;
      this.channelIndex = channelIndex;
      this.messageFilter = null;
    }

    public ChannelListener(KvDevice device, int channelIndex, CanMessageFilter messageFilter) {
      this.device = device;
      this.channelIndex = channelIndex;
      this.messageFilter = messageFilter;
    }

    /*
     * Listener for CAN messages and broadcasts them to bound clients
     */
    public void canMessageReceived(CanMessage msg) {
      int deviceIndex = deviceList.indexOf(device);
      if (deviceIndex != -1) {
        uiData.addChannelMessage(deviceIndex, channelIndex, msg);
        uiData.addLogMessage(deviceIndex, channelIndex, msg);

        Message receivedMsg =
            Message.obtain(null, MSG_READ_MESSAGE, deviceIndex, channelIndex);
        receivedMsg.obj = msg;
        incomingHandler.broadcast(receivedMsg);
      }
    }
  }
  
  private class BusStateListener implements ChipStateListener {
    
    private int deviceIndex, channelIndex;
    
    public BusStateListener(int deviceIndex, int channelIndex) {
      this.deviceIndex = deviceIndex;
      this.channelIndex = channelIndex;
    }
    
    /*
     * Listener for chip state events broadcasts them to bound clients
     */
    @Override
    public void chipStateEvent(ChipState chipState) {
      
      uiData.addLogMessage(deviceIndex, channelIndex, chipState);
      
      Message chipStateMsg = Message.obtain(null, MSG_CHIP_STATE, deviceIndex, channelIndex);
      chipStateMsg.obj = chipState;
      incomingHandler.broadcast(chipStateMsg);
    }
    
  }
  
  /*
   * Handler of incoming messages from clients.
   */
  private class IncomingHandler extends Handler {

    /*
    * Messages' replyTo fields must contain a valid Messenger to use for replies. Other Message
    * fields varies with the commands. Error reply is sent if arguments are not valid.
    */
    @Override
    public void handleMessage(Message msg) {
      Boolean isError = true;

      switch (msg.what) {

        // No additional arguments
        // Sends a MSG_REGISTER_CLIENT as an ACK
        case MSG_REGISTER_CLIENT:
          mClients.add(msg.replyTo);
          Message registerMsg = Message.obtain(null, MSG_REGISTER_CLIENT);
          sendReply(registerMsg, msg.replyTo);
          break;

        // No additional arguments
        case MSG_UNREGISTER_CLIENT:
          mClients.remove(msg.replyTo);
          break;

        // arg1 : device index
        // Reply contains device info bundle
        case MSG_DEVICE_INFO:
          if (msg.arg1 < deviceList.size()) {
            Message infoMsg = Message.obtain(null, CanLibService.MSG_DEVICE_INFO, msg.arg1, 0);
            infoMsg.setData(deviceList.get(msg.arg1).getDeviceInfo());
            sendReply(infoMsg, msg.replyTo);
            isError = false;
          }
          if (isError) {
            sendErrorReply(ERROR_ILLEGAL_ARGUMENT, msg.replyTo);
          }
          break;

        // arg1 : device index
        // arg2 : channel index
        // data : should contain strings with tags BUNDLE_KEY_CANMSG_ID and BUNDLE_KEY_CANMSG_DATA
        case MSG_SEND_MESSAGE:
          try {
            if (msg.arg1 < deviceList.size()) {
              List<KvChannel> channelList = channelMap.get(deviceList.get(msg.arg1));
              if (msg.arg2 < channelList.size()) {
                if (msg.obj.getClass().equals(CanMessage.class)) {
                  channelList.get(msg.arg2).write((CanMessage) (msg.obj));
                }
                isError = false;
              }
            }
          } catch (CanLibException e) {
            e.printStackTrace();
            isError = true;
          }
          if (isError) {
            sendErrorReply(ERROR_ILLEGAL_ARGUMENT, msg.replyTo);
          }
          break;

        // arg1 : device index
        // arg2 : channel index
        case MSG_SET_BUS_ON:
          try {
            if (msg.arg1 < deviceList.size()) {
              List<KvChannel> channelList = channelMap.get(deviceList.get(msg.arg1));
              if (msg.arg2 < channelList.size()) {
                channelList.get(msg.arg2).busOn();
                uiData.setBusStatus(msg.arg1, msg.arg2, true);
                isError = false;
              }
            }
          } catch (CanLibException e) {
            e.printStackTrace();
            isError = true;
          }
          if (isError) {
            sendErrorReply(ERROR_ILLEGAL_ARGUMENT, msg.replyTo);
          }
          break;

        // arg1 : device index
        // arg2 : channel index
        case MSG_SET_BUS_OFF:
          try {
            if (msg.arg1 < deviceList.size()) {
              List<KvChannel> channelList = channelMap.get(deviceList.get(msg.arg1));
              if (msg.arg2 < channelList.size()) {
                channelList.get(msg.arg2).busOff();
                uiData.setBusStatus(msg.arg1, msg.arg2, false);
                isError = false;
              }
            }
          } catch (CanLibException e) {
            e.printStackTrace();
            isError = true;
          }
          if (isError) {
            sendErrorReply(ERROR_ILLEGAL_ARGUMENT, msg.replyTo);
          }
          break;

        // No additional arguments
        case MSG_NUMBER_OF_DEVICES:
          Message numDevicesMsg =
              Message.obtain(null, MSG_NUMBER_OF_DEVICES, deviceList.size(), 0);
          sendReply(numDevicesMsg, msg.replyTo);
          break;

        // arg1 : device index
        case MSG_NUMBER_OF_CHANNELS:
          if (msg.arg1 < deviceList.size()) {
            Message numChannelsMsg = Message.obtain(null, MSG_NUMBER_OF_CHANNELS, msg.arg1,
                                                    deviceList.get(msg.arg1)
                                                              .getNumberOfChannels());
            sendReply(numChannelsMsg, msg.replyTo);
            isError = false;
          }
          if (isError) {
            sendErrorReply(ERROR_ILLEGAL_ARGUMENT, msg.replyTo);
          }
          break;

        // arg1 : device index
        case MSG_FLASH_LEDS:
          if (msg.arg1 < deviceList.size()) {
            deviceList.get(msg.arg1).flashLeds();
            isError = false;
          }
          if (isError) {
            sendErrorReply(ERROR_ILLEGAL_ARGUMENT, msg.replyTo);
          }
          break;

        // arg1 : device index
        // arg2 : channel index
        case MSG_UPDATE_SHARED_PREFERENCES:
          if (msg.arg1 < deviceList.size()) {
            if (msg.arg2 < channelMap.get(deviceList.get(msg.arg1)).size()) {
              readParameters(msg.arg1, msg.arg2);
              Message updateMsg =
                  Message.obtain(null, MSG_UPDATE_SHARED_PREFERENCES, msg.arg1, msg.arg2);
              sendReply(updateMsg, msg.replyTo);
              isError = false;
            }
          }
          if (isError) {
            sendErrorReply(ERROR_ILLEGAL_ARGUMENT, msg.replyTo);
          }
          break;

        // arg1 : device index
        // arg2 : channel index
        case MSG_WRITE_PARAMETERS:
          try {
            if (msg.arg1 < deviceList.size()) {
              if (msg.arg2 < channelMap.get(deviceList.get(msg.arg1)).size()) {
                writeParameters(msg.arg1, msg.arg2, false);
                isError = false;
              }
            }
          } catch (CanLibException e) {
            e.printStackTrace();
            isError = true;
          }
          if (isError) {
            sendErrorReply(ERROR_ILLEGAL_ARGUMENT, msg.replyTo);
          }
          break;

        // No additional arguments
        case MSG_WRITE_PARAMETERS_ALL_CHANNELS:
          try {
            writeParameters(0, 0, true);
          } catch (CanLibException e) {
            e.printStackTrace();
          }
          break;

        case MSG_READ_PARAMETERS:
          try {
            if (msg.arg1 < deviceList.size()) {
              if (msg.arg2 < channelMap.get(deviceList.get(msg.arg1)).size()) {
                Message paramMsg =
                    Message.obtain(null, CanLibService.MSG_READ_PARAMETERS, msg.arg1, msg.arg2);
                paramMsg.obj =
                    channelMap.get(deviceList.get(msg.arg1)).get(msg.arg2).getBusParams();
                sendReply(paramMsg, msg.replyTo);
              }
            }
          } catch (CanLibException e) {
            e.printStackTrace();
            sendErrorReply(ERROR_ILLEGAL_ARGUMENT, msg.replyTo);
          }
          break;

        case MSG_TOGGLE_VIRTUAL_DEVICE:
          SharedPreferences preferences =
              PreferenceManager.getDefaultSharedPreferences(CanLibService.this);
          canlib.setVirtualDeviceState(
              preferences.getBoolean(getString(R.string.preference_enablevirtual), true));
          break;

        default:
          sendErrorReply(ERROR_ILLEGAL_ARGUMENT, msg.replyTo);
          break;
      }
    }

    private void sendReply(Message msg, Messenger replyTo) {
      try {
        replyTo.send(msg);
      } catch (RemoteException e) {
        // Failed to send reply to client, print trace.
        e.printStackTrace();
      }
    }

    private void broadcast(Message msg) {
      for (Messenger target : mClients) {
        try {
          Message broadcastMsg = Message.obtain();
          broadcastMsg.copyFrom(msg);
          target.send(broadcastMsg);
        } catch (RemoteException e) {
          // Failed to send message to client. Print trace and try next client.
          e.printStackTrace();
        }
      }
    }

    private void sendErrorReply(int errorMessage, Messenger replyTo) {
      try {
        replyTo.send(Message.obtain(null, errorMessage));
      } catch (RemoteException e) {
        // Failed to send reply to client. Print trace.
        e.printStackTrace();
      }
    }
  }

  /*
   * Initializes CanLib and starts a handler for incoming messages
   */
  private class BackgroundThread extends Thread {

    @Override
    public void run() {
      // Create a looper thread for the message handler
      Looper.prepare();
      incomingHandler = new IncomingHandler();
      mMessenger = new Messenger(incomingHandler);
      // Initialize CanLib
      canlib = CanLib.getInstance(CanLibService.this);

      SharedPreferences preferences =
          PreferenceManager.getDefaultSharedPreferences(CanLibService.this);
      canlib.setVirtualDeviceState(
          preferences.getBoolean(getString(R.string.preference_enablevirtual), true));

      // Start looper thread
      Looper.loop();
    }
  }
}