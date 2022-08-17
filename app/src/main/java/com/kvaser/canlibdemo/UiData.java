package com.kvaser.canlibdemo;

import android.app.*;

import com.kvaser.canlib.*;

import java.util.*;
/**
 * Provides methods to store and get data associated with devices and channels persistently
 * over the life of the CanLibDemo app.
 */
public class UiData extends Application {

  private static final int MAX_LIST_SIZE = 1000;

  private List<List<List<CanMessage>>> messageList;
  private List<LogMessage> logMessageList = new ArrayList<>();
  private List<boolean[]> busStatus;
  
  synchronized public void setBusStatus(int deviceIndex, int channelIndex, boolean status) {
    if (busStatus != null) {
      if (deviceIndex < busStatus.size()) {
        if (channelIndex < busStatus.get(deviceIndex).length) {
          busStatus.get(deviceIndex)[channelIndex] = status;
        }
      }
    }
  }
  
  synchronized public boolean getBusStatus(int deviceIndex, int channelIndex) {
    if (busStatus != null) {
      if (deviceIndex < busStatus.size()) {
        if (channelIndex < busStatus.get(deviceIndex).length) {
          return busStatus.get(deviceIndex)[channelIndex];
        }
      }
    }
    return false;
  }
  
  synchronized public CanMessage[] getChannelMessages(int deviceIndex, int channelIndex) {
    if (messageList != null) {
      if (deviceIndex < messageList.size()) {
        if (channelIndex < messageList.get(deviceIndex).size()) {
          return messageList.get(deviceIndex).get(channelIndex).toArray(
              new CanMessage[messageList.get(deviceIndex).get(channelIndex).size()]);
        }
      }
    }
    return null;
  }
  
  synchronized public void addChannelMessage(int deviceIndex, int channelIndex,
                                             CanMessage message) {
    if (messageList != null) {
      if (deviceIndex < messageList.size()) {
        if (channelIndex < messageList.get(deviceIndex).size()) {
          while (messageList.get(deviceIndex).get(channelIndex).size() >= MAX_LIST_SIZE) {
            messageList.get(deviceIndex).get(channelIndex).remove(0);
          }
          messageList.get(deviceIndex).get(channelIndex).add(message);
        }
      }
    }
  }
  
  synchronized public void addLogMessage(int deviceIndex, int channelIndex, CanMessage message) {
    if (logMessageList != null) {
      while (logMessageList.size() >= MAX_LIST_SIZE) {
        logMessageList.remove(0);
      }
      logMessageList.add(new LogMessage(deviceIndex, channelIndex, message));
    }
  }
  
  synchronized public void addLogMessage(int deviceIndex, int channelIndex, ChipState chipState) {
    if (logMessageList != null) {
      logMessageList.add(new LogMessage(deviceIndex, channelIndex, chipState));
    }
  }
  
  synchronized public LogMessage[] getLogMessages() {
    LogMessage[] logArray = new LogMessage[logMessageList.size()];
    return logMessageList.toArray(logArray);
  }
  
  synchronized public void clearLogMessages() {
    if (logMessageList != null) {
      logMessageList.clear();
    }
  }

  synchronized public void removeDevice(int deviceIndex) {
    if (messageList != null) {
      if (deviceIndex < messageList.size()) {
        messageList.remove(deviceIndex);
        busStatus.remove(deviceIndex);
      }
    }
  }
  
  synchronized public void addDevice() {
    if (messageList == null) {
      messageList = new ArrayList<>();
    }
    if (busStatus == null) {
      busStatus = new ArrayList<>();
    }
    messageList.add(new ArrayList<List<CanMessage>>());
    busStatus.add(new boolean[] {});
  }
  
  synchronized public void setNumberOfChannels(int deviceIndex, int numberOfChannels) {
    if (messageList != null) {
      if (deviceIndex < messageList.size()) {
        for (int i = 0; i < numberOfChannels; i++) {
          messageList.get(deviceIndex).add(new ArrayList<CanMessage>());
          busStatus.remove(deviceIndex);
          busStatus.add(deviceIndex, new boolean[numberOfChannels]);
        }
      }
    }
  }
  
  public class LogMessage {
    
    public CanMessage canMessage = null;
    public ChipState chipState = null;
    public int deviceIndex;
    public int channelIndex;

    LogMessage(int deviceIndex, int channelIndex, CanMessage canMessage) {
      this.deviceIndex = deviceIndex;
      this.channelIndex = channelIndex;
      this.canMessage = canMessage;
    }

    LogMessage(int deviceIndex, int channelIndex, ChipState chipState) {
      this.deviceIndex = deviceIndex;
      this.channelIndex = channelIndex;
      this.chipState = chipState;
    }
  }
}
