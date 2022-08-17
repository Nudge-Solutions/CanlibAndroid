package com.kvaser.canlib;

import android.os.*;

import java.util.*;
/**
 * Represents a Kvaser device. The class can be used to read out identification data for the device
 * and create KvChannel objects
 */
public class KvDevice {

  private final KvDeviceInterface deviceDriver;

  KvDevice(KvDeviceInterface deviceDriver) {
    this.deviceDriver = deviceDriver;
  }

  /**
   * Returns a boolean describing whether the device is virtual.
   *
   * @return True if the device is virtual.
   */
  public boolean isVirtual() { return deviceDriver.isVirtual(); }

  /**
   * Returns a bundle containing information about the device. The bundle contains only strings
   * which directly can be presented in a UI.
   *
   * @return Bundle containing information about the device.
   */
  public Bundle getDeviceInfo() {
    return deviceDriver.getDeviceInfo();
  }

  /**
   * Returns string representing the device's EAN.
   *
   * @return The device's EAN as an Ean object;
   */
  public Ean getEan() {
    return deviceDriver.getEan();
  }

  /**
   * Returns an integer representing the device's serial number.
   *
   * @return The device's serial number.
   */
  public int getSerialNumber() {
    return deviceDriver.getSerialNumber();
  }

  /**
   * Returns the number of channels that the device has.
   *
   * @return The number of channels that the device has.
   */
  public int getNumberOfChannels() {
    return deviceDriver.getNumberOfChannels();
  }

  /**
   * Flashes the device's LEDs 5 times.
   */
  public void flashLeds() {
    deviceDriver.flashLeds();
  }

  /**
   * Opens a CAN channel (circuit) and returns a KvChannel object which is used for CAN
   * communication.
   *
   * It is possible to create several KvChannel objects for the same physical CAN channel using
   * this interface. For example in the case when two threads wants to access the same channel.
   *
   * Example calls:
   * <pre>
   * {@code
   * KvChannel ch1 = device.openChannel(0, EnumSet.of(ChannelFlags.REQUIRE_EXTENDED));
   * KvChannel ch2 = device.openChannel(1, null);
   * }
   * </pre>
   *
   * @param channelIndex The channel to open.
   * @param flags        Flags with additional settings to apply to the channel. The flags are
   *                     packed in an EnumSet&lt;ChannelFlags&gt;. null may be passed as this
   *                     parameter in case no flags shall be set.
   * @return A channel object for the opened channels.
   * @throws CanLibException If channel cannot be opened.
   */
  public KvChannel openChannel(int channelIndex,
                               EnumSet<KvChannel.ChannelFlags> flags) throws CanLibException {
    return new KvChannel(channelIndex, deviceDriver, this, flags);
  }
}
