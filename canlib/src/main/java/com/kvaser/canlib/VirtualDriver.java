package com.kvaser.canlib;

import android.os.*;

import com.kvaser.canlib.CanLibException.ErrorCode;
import com.kvaser.canlib.CanLibException.ErrorDetail;

import java.util.*;

/**
 * Device driver for a virtual can device.
 */
class VirtualDriver implements KvDeviceInterface {

  private static final int NUMBER_OF_CHANNELS = 2;

  private long startTimestamp;
  private int errorCounter = 0;
  private final List<CanChannelEventListener> canChannelListeners = new ArrayList<>();
  private final ChannelState[] channelStates = new ChannelState[NUMBER_OF_CHANNELS];

  VirtualDriver() {
    // Initialize default bus parameters
    for (int i = 0; i < NUMBER_OF_CHANNELS; i++) {
      channelStates[i] = new ChannelState();
      channelStates[i].busParams = new CanBusParams(); //Use default settings
      channelStates[i].busIsOn = false;
      channelStates[i].driverType = CanDriverType.NORMAL;
    }
    startTimestamp = System.nanoTime();
  }

  public void close() {
    //Unused in the virtual driver
  }

  public int getNumberOfChannels() {
    return NUMBER_OF_CHANNELS;
  }

  public boolean isVirtual() {
    return true;
  }

  public void setBusParams(int channelIndex, CanBusParams busParams) throws CanLibException {
    checkChannelIndex(channelIndex);
    channelStates[channelIndex].busParams = busParams;
  }

  public CanBusParams getBusParams(int channelIndex) throws CanLibException {
    checkChannelIndex(channelIndex);
    return channelStates[channelIndex].busParams;
  }

  public void busOn(int channelIndex) throws CanLibException {
    checkChannelIndex(channelIndex);
    channelStates[channelIndex].busIsOn = true;
  }

  public void busOff(int channelIndex) throws CanLibException {
    checkChannelIndex(channelIndex);
    channelStates[channelIndex].busIsOn = false;
  }

  public void setBusOutputControl(int channelIndex,
                                  CanDriverType driverType) throws CanLibException {
    checkChannelIndex(channelIndex);
    channelStates[channelIndex].driverType = driverType;
  }

  public CanDriverType getBusOutputControl(int channelIndex) throws CanLibException {
    checkChannelIndex(channelIndex);
    return channelStates[channelIndex].driverType;
  }

  public void write(int channelIndex, CanMessage msg) throws CanLibException {
    checkChannelIndex(channelIndex);

    if (channelStates[channelIndex].busIsOn) {
      errorCounter = 0;

      if ((msg.isFlagSet(CanMessage.MessageFlags.EXTENDED_ID) && ((msg.id & 0x7FFFFFFF) >= (1
                                                                                            << 29)))
          || (msg.isFlagSet(CanMessage.MessageFlags.STANDARD_ID) && (msg.id >= (1 << 11)))) {
        // ID out of range
        throw new CanLibException(ErrorCode.ERR_PARAM, ErrorDetail.ILLEGAL_ID);
      }

      //Send TX acknowledgement to listeners
      CanMessage txAckMsg = new CanMessage(msg);
      txAckMsg.direction = CanMessage.Direction.TX;
      txAckMsg.setFlag(CanMessage.MessageFlags.TX_ACK);
      txAckMsg.time = getTimestamp();
      for (CanChannelEventListener listener : canChannelListeners) {
        if (listener.getChannelIndex() == channelIndex) {
          listener.canChannelEvent(CanChannelEventListener.CanChannelEventType.MESSAGE, txAckMsg);
        }
      }

      //Send RX message to listeners
      CanMessage rxMsg = new CanMessage(msg);
      rxMsg.direction = CanMessage.Direction.RX;
      rxMsg.time = getTimestamp();
      for (CanChannelEventListener listener : canChannelListeners) {
        //Loopback to all other channels that are bus on
        if (listener.getChannelIndex() != channelIndex && channelStates[listener
            .getChannelIndex()].busIsOn) {
          listener.canChannelEvent(CanChannelEventListener.CanChannelEventType.MESSAGE, rxMsg);
        }
      }

      //Find if there is at least one other channel which is bus on. (No one has to be listening.)
      boolean messageWasReceived = false;
      for (int i = 0; i < NUMBER_OF_CHANNELS; i++) {
        if (i != channelIndex && channelStates[i].busIsOn) {
          messageWasReceived = true;
        }
      }

      //Send error frame if receiving channel(s) was off bus
      if (!messageWasReceived) {
        CanMessage errorMsg = new CanMessage(msg);
        errorMsg.direction = CanMessage.Direction.RX;
        errorMsg.time = getTimestamp();
        errorMsg.setFlag(CanMessage.MessageFlags.ERROR_FRAME);
        for (CanChannelEventListener listener : canChannelListeners) {
          if (listener.getChannelIndex() == channelIndex) {
            listener.canChannelEvent(CanChannelEventListener.CanChannelEventType.MESSAGE, errorMsg);
          }
        }
      }
    } else {
      //Send bus off chip state
      errorCounter++;
      ChipState chipState = new ChipState();
      chipState.busStatus = EnumSet.of(ChipState.BusStatus.BUSOFF);
      chipState.channel = (byte) channelIndex;
      chipState.txErrorCounter = errorCounter;
      chipState.time = getTimestamp();
      for (CanChannelEventListener listener : canChannelListeners) {
        if (listener.getChannelIndex() == channelIndex) {
          listener
              .canChannelEvent(CanChannelEventListener.CanChannelEventType.CHIP_STATE, chipState);
        }
      }
    }
  }

  public Bundle getDeviceInfo() {
    Bundle b = new Bundle();
    b.putString("Device Name", "Kvaser Virtual CAN Driver");
    b.putString("Hardware Type", "Virtual (1)");
    b.putString("Manufacturer", "Kvaser AB");
    b.putString("Card EAN", "00-00000-00000-0");
    b.putString("Serial Number", "0");
    b.putString("Firmware Version", "0.0.0");
    b.putString("Hardware Revision", "0.0");
    return b;
  }

  public Ean getEan() {
    return new Ean();
  }

  public int getSerialNumber() {
    return 0;
  }

  public void flashLeds() {
    //Flashing virtual LEDs...
  }

  public void registerCanChannelEventListener(CanChannelEventListener listener) {
    canChannelListeners.add(listener);
  }

  public void unregisterCanChannelEventListener(CanChannelEventListener listener) {
    canChannelListeners.remove(listener);
  }

  @Override
  public void UsbDataReceived(byte[] bytes) {
    //Unused in the virtual driver
  }

  private void checkChannelIndex(int channelIndex) throws CanLibException {
    if ((channelIndex < 0) || (channelIndex >= NUMBER_OF_CHANNELS)) {
      throw new CanLibException(ErrorCode.ERR_PARAM, ErrorDetail.NON_EXISTING_CHANNEL);
    }
  }

  private long getTimestamp() {
    //Return a timestamp with 10µs resolution
    return (System.nanoTime() - startTimestamp) / 10000;
  }

  private class ChannelState {

    private CanBusParams busParams;
    private  boolean busIsOn;
    private CanDriverType driverType;
  }
}
