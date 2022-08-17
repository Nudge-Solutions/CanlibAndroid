package com.kvaser.canlib;

import android.os.*;
/**
 * Interface for a Kvaser device driver.
 */
interface KvDeviceInterface extends UsbListener {

  void close();
  int getNumberOfChannels();
  boolean isVirtual();
  void setBusParams(int channelIndex, CanBusParams busParams) throws CanLibException;
  CanBusParams getBusParams(int channelIndex) throws CanLibException;
  void busOn(int channelIndex) throws CanLibException;
  void busOff(int channelIndex) throws CanLibException;
  void setBusOutputControl(int channelIndex, CanDriverType driverType) throws CanLibException;
  CanDriverType getBusOutputControl(int channelIndex) throws CanLibException;
  void write(int channelIndex, CanMessage msg) throws CanLibException;
  Bundle getDeviceInfo();
  Ean getEan();
  int getSerialNumber();
  void flashLeds();
  void registerCanChannelEventListener(CanChannelEventListener listener);
  void unregisterCanChannelEventListener(CanChannelEventListener listener);
}