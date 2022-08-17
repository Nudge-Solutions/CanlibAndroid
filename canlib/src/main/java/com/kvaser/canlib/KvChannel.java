package com.kvaser.canlib;

import android.os.*;
import android.support.annotation.*;

import java.util.*;

import com.kvaser.canlib.CanLibException.ErrorDetail;
import com.kvaser.canlib.CanLibException.ErrorCode;

/**
 * The KvChannel class provides methods for performing operations a CAN channel.
 */
public class KvChannel {

  private final List<CanMessageListener> canMessageListeners = new ArrayList<>();
  private final List<CanMessageFilter> filterList = new ArrayList<>();
  private final List<ChipStateListener> chipStateListeners = new ArrayList<>();
  private AddressingType defaultAddressingType;
  private boolean acceptLargeDlc;
  private int channelIndex;
  private KvDeviceInterface deviceDriver;
  private KvDevice kvDevice;
  private EventListener canEventListener;

  /**
   * @param channelIndex The channel index on this device defines which CAN channel this KvChannel
   *                     object shall be connected to.
   * @param deviceDriver The driver for the device
   * @param kvDevice     The device object for the device
   * @param flags        Additional options to apply to the KvChannel object, may be null if no
   *                     flags shall be considered.
   * @throws CanLibException if the {@link #channelIndex} is not valid for this device or in case
   *                         {@link #deviceDriver} or {@link #kvDevice} is null.
   */
  KvChannel(int channelIndex, @NonNull KvDeviceInterface deviceDriver, @NonNull KvDevice kvDevice,
            @Nullable EnumSet<ChannelFlags> flags) throws CanLibException {
    assertParam((deviceDriver != null), ErrorDetail.NULL_ARGUMENT, "deviceDriver");
    assertParam((kvDevice != null), ErrorDetail.NULL_ARGUMENT, "kvDevice");
    assertParam(((channelIndex < deviceDriver.getNumberOfChannels()) && (channelIndex >= 0)),
                ErrorDetail.NON_EXISTING_CHANNEL, channelIndex);

    this.channelIndex = channelIndex;
    this.deviceDriver = deviceDriver;
    this.kvDevice = kvDevice;

    // Evaluate flags
    if (flags != null) {

      if (flags.contains(ChannelFlags.EXCLUSIVE)) {
        // Try to obtain exclusive access to channel
        if (!CanChannelAccess.getExclusiveAccess(kvDevice, channelIndex)) {
          throw new CanLibException(ErrorCode.ERR_ACCESS, ErrorDetail.EXCLUSIVE_ACCESS_FAILED);
        }
        // Try to obtain non-exclusive access to channel
      } else {
        if (!CanChannelAccess.getAccess(kvDevice, channelIndex)) {
          throw new CanLibException(ErrorCode.ERR_ACCESS, ErrorDetail.CHANNEL_LOCKED);
        }
      }

      // TODO: ACCEPT_VIRTUAL shall not be implemented now, maybe in the future
      if (flags.contains(ChannelFlags.ACCEPT_VIRTUAL)) {
        // Do what?
      }
      // Set a boolean field to say that we may accept large DLC. But how is this handled in the
      // communication with the device? The CmdLogMessage and the CmdTxCanMessage only have room for
      // 8 data bytes.
      this.acceptLargeDlc = flags.contains((ChannelFlags.ACCEPT_LARGE_DLC));

      // TODO: CAN_FD shall be implemented in the future
      if (flags.contains(ChannelFlags.CAN_FD)) {
        // Shall this be supported by the Android CanLib? - Yes, in the future.
      }

      // TODO: CAN_FD_NON_ISO shall be implemented whenever CAN_FD is implemented
      if (flags.contains(ChannelFlags.CAN_FD_NON_ISO)) {
        // Shall this be supported by the Android CanLib? - Yes, in the future.
      }

      if (flags.contains(ChannelFlags.REQUIRE_EXTENDED)) {
        defaultAddressingType = AddressingType.EXTENDED;
      } else {
        defaultAddressingType = AddressingType.STANDARD;
      }
    } else {
      if (!CanChannelAccess.getAccess(kvDevice, channelIndex)) {
        throw new CanLibException(ErrorCode.ERR_ACCESS, ErrorDetail.CHANNEL_LOCKED);
      }
    }
  }

  /**
   * Marks a channel as closed so that its access to the physical channel is revoked (and freed for
   * other KvChannels).
   */
  public void close() {
    CanChannelAccess.releaseAccess(kvDevice, channelIndex);
  }

  /**
   * Returns the device to which the channel is connected.
   *
   * @return Returns the device to which the channel is connected.
   */
  public KvDevice getDevice() {
    return kvDevice;
  }

  /**
   * Returns a bundle containing information about the device. The bundle contains only strings
   * which directly can be presented in a UI.
   *
   * @return Returns a bundle containing information about the device.
   */
  public Bundle getDeviceInfo() {
    return deviceDriver.getDeviceInfo();
  }

  /**
   * Gets the current bus timing parameters for the KvChannel
   *
   * @return The applied bus parameters.
   */
  public CanBusParams getBusParams() throws CanLibException {
    return deviceDriver.getBusParams(channelIndex);
  }

  /**
   * Sets the bus timing parameters for the KvChannel.
   *
   * In case the bus is on this call will temporarily set busoff while updating the parameters and
   * then go back to bus on.
   *
   * Note that this will only request setting of parameters to the device. In case the parameters
   * are not applied (e.g. if the values are invalid) or if the values are changed (e.g. by another
   * thread or app) then the active parameters may differ from the ones set with this method. To
   * read out the actual applied values use {@link #getBusParams()}.
   *
   * The number of sampling points used are always set to 1.
   *
   * @param busParams The bus parameters that shall be applied.
   * @throws CanLibException in case of illegal parameter setting
   */
  public void setBusParams(@NonNull CanBusParams busParams) throws CanLibException {
    assertParam((busParams != null), ErrorDetail.NULL_ARGUMENT, "busParams");
    assertParam((busParams.bitRate > 0), ErrorDetail.ILLEGAL_BITRATE, busParams.bitRate);
    assertParam(((busParams.sjw >= 1) && (busParams.sjw <= 4)), ErrorDetail.ILLEGAL_SJW,
                busParams.sjw);
    assertParam(((busParams.tseg1 >= 1) && (busParams.tseg1 <= 16)), ErrorDetail.ILLEGAL_TSEG1,
                busParams.tseg1);
    assertParam(((busParams.tseg2 >= 1) && (busParams.tseg2 <= 8)), ErrorDetail.ILLEGAL_TSEG2,
                busParams.tseg2);

    deviceDriver.setBusParams(channelIndex, busParams);
  }

  /**
   * Takes the specified channel on bus.
   */
  public void busOn() throws CanLibException {
    deviceDriver.busOn(channelIndex);
  }

  /**
   * Takes the specified channel off bus.
   */
  public void busOff() throws CanLibException {
    deviceDriver.busOff(channelIndex);
  }

  /**
   * Gets the driver type for a CAN controller.
   *
   * @return CAN driver type
   */
  public CanDriverType getBusOutputControl() throws CanLibException {
    return deviceDriver.getBusOutputControl(channelIndex);
  }

  /**
   * Sets the driver type for a CAN controller. This loosely corresponds to the bus
   * output control register in the CAN controller, hence the name of this function. CanLib does
   * not allow for direct manipulation of the bus output control register; instead, symbolic
   * constants are used to select the desired driver type.
   * Note: Not all CAN driver types are supported on all cards.
   *
   * @param driverType CAN driver type
   */
  public void setBusOutputControl(CanDriverType driverType) throws CanLibException {
    deviceDriver.setBusOutputControl(channelIndex, driverType);
  }

  /**
   * Sends a CAN message. The call returns immediately after queuing  the message to
   * the driver.
   * Note: The message has been queued for transmission when this calls return. It has not
   * necessarily been sent.
   *
   * @param msg The CAN message to send
   */
  public void write(CanMessage msg) throws CanLibException {
    CanMessage.MessageFlags addressTypeFlag;
    if (msg.flags.contains(CanMessage.MessageFlags.EXTENDED_ID)) {
      addressTypeFlag = CanMessage.MessageFlags.EXTENDED_ID;
    } else if (msg.flags.contains(CanMessage.MessageFlags.STANDARD_ID)) {
      addressTypeFlag = CanMessage.MessageFlags.STANDARD_ID;
    } else if (defaultAddressingType == AddressingType.EXTENDED) {
      addressTypeFlag = CanMessage.MessageFlags.EXTENDED_ID;
    } else {
      addressTypeFlag = CanMessage.MessageFlags.STANDARD_ID;
    }
    msg.setFlag(addressTypeFlag);

    //Clear all flags except for EXTENDED_ID, STANDARD_ID and REMOTE_REQUEST
    for (CanMessage.MessageFlags flag : msg.flags) {
      if (flag != CanMessage.MessageFlags.EXTENDED_ID &&
          flag != CanMessage.MessageFlags.STANDARD_ID &&
          flag != CanMessage.MessageFlags.REMOTE_REQUEST) {
        msg.flags.remove(flag);
      }
    }

    if (!isDlcOk(msg.dlc)) {
      throw new CanLibException(ErrorCode.ERR_PARAM, ErrorDetail.ILLEGAL_DLC);
    }
    msg.direction = CanMessage.Direction.TX;
    msg.time = -1;
    deviceDriver.write(channelIndex, msg);
  }

  /**
   * Registers KvChannel's listener with the driver if it was not already registered.
   */
  private void registerCanListener() {
    if (canEventListener == null) {
      canEventListener = new EventListener();
      deviceDriver.registerCanChannelEventListener(canEventListener);
    }
  }

  /**
   * Unregisters KvChannel's listener with the driver if it was registered.
   */
  private void unregisterCanListener() {
    if ((canEventListener != null) && canMessageListeners.isEmpty() && chipStateListeners
        .isEmpty()) {
      deviceDriver.unregisterCanChannelEventListener(canEventListener);
      canEventListener = null;
    }
  }

  /**
   * Registers a message listener.
   *
   * @param listener The CanMessageListener to register
   */
  public void registerCanMessageListener(CanMessageListener listener) {
    synchronized (canMessageListeners) {
      registerCanListener();
      canMessageListeners.add(listener);
    }
  }

  /**
   * Unregisters a message listener.
   *
   * @param listener The CanMessageListener to unregister
   */
  public void unregisterCanMessageListener(CanMessageListener listener) {
    synchronized (canMessageListeners) {
      canMessageListeners.remove(listener);
      unregisterCanListener();
    }
  }

  /**
   * Adds a filter to the channel's list of filters. The filter can be altered to change filter
   * characteristics after it has been added. Additional calls with the same filter objects are
   * ignored. If multiple filters are added, they are used in parallel i.e. if any of the filters
   * passes the message then the message passes all filters.
   *
   * @param filter The filter to add.
   */
  public void addFilter(CanMessageFilter filter) {
    synchronized (filterList) {
      if (filter != null) {
        if (!filterList.contains(filter)) {
          filterList.add(filter);
        }
      }
    }
  }

  /**
   * Removes a filter from the channel's filter list.
   *
   * @param filter The filter to remove.
   */
  public void removeFilter(CanMessageFilter filter) {
    synchronized (filterList) {
      if (filter != null) {
        filterList.remove(filter);
      }
    }
  }

  /**
   * Removes all filters on the channel.
   */
  public void clearFilters() {
    synchronized (filterList) {
      filterList.clear();
    }
  }

  /**
   * Registers a chip state listener.
   *
   * @param listener The ChipStateListener to register
   */
  public void registerChipStateListener(ChipStateListener listener) {
    synchronized (chipStateListeners) {
      registerCanListener();
      chipStateListeners.add(listener);
    }
  }

  /**
   * Unregisters a chip state listener.
   *
   * @param listener The ChipStateListener to unregister
   */
  public void unregisterChipStateListener(ChipStateListener listener) {
    synchronized (chipStateListeners) {
      unregisterCanListener();
      chipStateListeners.remove(listener);
    }
  }

  /**
   * Returns the index of the channel associated with the KvChannel object.
   *
   * @return Returns the channel's index
   */
  public int getChannelIndex() {
    return channelIndex;
  }

  private boolean isDlcOk(int dlc) {
    if (acceptLargeDlc) {
      return ((dlc <= 15) && (dlc >= 0));
    } else {
      return ((dlc <= 8) && (dlc >= 0));
    }

  }

  private boolean isDlcOk(CanMessage message) {
    return isDlcOk(message.dlc);
  }

  private void fixDlc(CanMessage message) {
    if (!isDlcOk(message)) {
      if (acceptLargeDlc) {
        message.dlc = 15;
      } else {
        message.dlc = 8;
      }
    }
  }

  private void assertParam(boolean success, CanLibException.ErrorDetail detail,
                           long value) throws CanLibException {
    if (!success) {
      throw new CanLibException(ErrorCode.ERR_PARAM, detail, value);
    }
  }

  private void assertParam(boolean success, CanLibException.ErrorDetail detail,
                           String msg) throws CanLibException {
    if (!success) {
      throw new CanLibException(ErrorCode.ERR_PARAM, detail, msg);
    }
  }

  private enum AddressingType {STANDARD, EXTENDED}

  /**
   * Enum containing flags used for configuring KvChannels.
   */
  public enum ChannelFlags {

    /**
     * Don't allow sharing of this circuit between applications.<br>
     * This flag is used in {@link KvDevice#openChannel(int, EnumSet)}.
     */
    EXCLUSIVE,
    /**
     * Set extended addressing as default.<br>
     * This flag is used in {@link KvDevice#openChannel(int, EnumSet)}. If no frame-type flag is
     * specified in a call to {@link KvChannel#write(CanMessage)}, it is assumed that extended CAN
     * should be used.
     */
    REQUIRE_EXTENDED,
    /**
     * Allow opening of virtual channels as well as physical channels.<br>
     * This flag is used in {@link KvDevice#openChannel(int, EnumSet)}.
     *
     * @deprecated This flag is not supported by the current CanLib version on Android. It may be
     * removed or it may be implemented in the future. Until then - do not use it.
     */
    ACCEPT_VIRTUAL,
    /**
     * Accepting DLC &gt; 8.<br>
     * This flag is used in {@link KvDevice#openChannel(int, EnumSet)}.
     * The channel will accept messages with DLC (Data Length Code) greater than 8. If this flag is
     * not used, a message with DLC &gt; 8 will always be reported or transmitted as a message with
     * DLC = 8. If the ACCEPT_LARGE_DLC flag is used, the message will be sent and/or
     * received
     * with the true DLC, which can be at most 15.
     * Note: The length of the message is always at most 8.
     */
    ACCEPT_LARGE_DLC,
    /**
     * The channel will use the CAN FD protocol.<br>
     * This flag is used in {@link KvDevice#openChannel(int, EnumSet)}.
     *
     * @deprecated This flag is not supported by the current CanLib version on Android. It may be
     * removed or it may be implemented in the future. Until then - do not use it.
     */
    CAN_FD,
    /**
     * The channel will use the CAN FD NON-ISO  protocol.<br>
     * This flag is used in {@link KvDevice#openChannel(int, EnumSet)}.
     *
     * @deprecated This flag is not supported by the current CanLib version on Android. It may be
     * removed or it may be implemented in the future. Until then - do not use it.
     */
    CAN_FD_NON_ISO
  }

  /**
   * Listener for channel events in the device driver.
   */
  private class EventListener implements CanChannelEventListener {

    public int getChannelIndex() {
      return channelIndex;
    }

    public void canChannelEvent(CanChannelEventType eventType, Object eventData) {
      switch (eventType) {
        case MESSAGE:
          if (eventData instanceof CanMessage) {
            CanMessage canMessage = (CanMessage) eventData;
            fixDlc(canMessage);
            synchronized (canMessageListeners) {
              for (CanMessageListener listener : canMessageListeners) {
                boolean messagePassed = true;
                synchronized (filterList) {
                  if (filterList.size() > 0) {
                    // Use filters in parallel, i.e. message passes if any filter returns true
                    messagePassed = false;
                    for (CanMessageFilter filter : filterList) {
                      if (filter.filter(canMessage)) {
                        messagePassed = true;
                        break;
                      }
                    }
                  }
                }
                if (messagePassed) {
                  listener.canMessageReceived(canMessage);
                }
              }
            }
          }
          break;

        case CHIP_STATE:
          if (eventData instanceof ChipState) {
            synchronized (chipStateListeners) {
              for (ChipStateListener listener : chipStateListeners) {
                listener.chipStateEvent((ChipState) eventData);
              }
            }
          }
          break;

        default:
          // Unknown/unhandled event
          break;
      }
    }
  }
}
