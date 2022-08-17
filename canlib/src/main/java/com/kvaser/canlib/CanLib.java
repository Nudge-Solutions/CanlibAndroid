package com.kvaser.canlib;

import android.content.Context;

/**
 * Main package class. Create a CanLib class object to instantiate the library. Use the
 * library object to get KvDevice objects. Use these to open KvChannels through which communication
 * with physical channels can be made. Run CanLib in a separate thread from the UI to get a
 * responsive UI.
 *
 * Example use in your activity/service (without error checking):
 * <pre>
 * {@code
 * CanLib canLib = new CanLib(this);
 *
 * int deviceIndex = 0;
 * int channelIndex = 0;
 *
 * KvDevice device;
 * KvChannel channel;
 *
 * if (deviceIndex < canLib.getNumberOfDevices()){
 *   device = canLib.getDevice(deviceIndex);
 *   if(channelIndex < device.getNumberOfChannels()){
 *     channel = device.openChannel(channelIndex, EnumSet.of(ChannelFlags.REQUIRE_EXTENDED));
 *     channel.setBusParams(new CanBusParams(CanBusParams.DefaultBitRates.BITRATE_250K));
 *     channel.busOn();
 *     channel.setBusOutputControl(Canlib.CanDriverType.NORMAL);
 *
 *     CanMessage msg = new CanMessage();
 *     msg.dlc = 8;
 *     msg.data[0] = 1;
 *     msg.data[1] = 2;
 *     msg.data[2] = 4;
 *     msg.data[3] = 8;
 *     msg.data[4] = 16;
 *     msg.data[5] = 32;
 *     msg.data[6] = 64;
 *     msg.data[7] = (byte) 128;
 *     msg.id = 0x123;
 *     channel.write(msg);
 *   }
 * }
 * }
 * </pre>
 */
public final class CanLib {

  private static CanLib instance;
  private static UsbHandler usbHandler;
  private static KvDeviceInterface virtualDriver;
  private static boolean virtualDeviceEnabled;
  
  private CanLib() {
  }

  /**
   * Singleton method for getting the library instance. Instantiates CanLib with the context in
   * which the application is run if the library was not previously instantiated. The context is
   * used for managing USB permissions.
   *
   * @param context The context of the activity instantiating the library.
   */
  public static CanLib getInstance(Context context) {
    if (instance == null) {
      instance = new CanLib();
      usbHandler = new UsbHandler(context);
      virtualDriver = new VirtualDriver();
      virtualDeviceEnabled = true;
    }
    return instance;
  }

  /**
   * Enables or disables the virtual device.
   *
   * @param state True enables the virtual device, false disables.
   */
  public void setVirtualDeviceState(boolean state)
  {
    virtualDeviceEnabled = state;
  }
  
  /**
   * Returns the current number of connected devices, including the virtual device.
   *
   * @return Returns the current number of connected devices.
   */
  public int getNumberOfDevices() {
    if (virtualDeviceEnabled) {
      return usbHandler.getNumberOfDevices() + 1;
    }
    else {
      return usbHandler.getNumberOfDevices();
    }
  }
  
  /**
   * Returns a KvDevice object which represents one Kvaser device.
   *
   * @param deviceIndex Specifies which device to return.
   * @return Returns the specified KvDevice. Returns null if the device does not exist.
   */
  public KvDevice getDevice(int deviceIndex) {
    if (virtualDeviceEnabled) {
      if (deviceIndex == 0) {
        return new KvDevice(virtualDriver);
      } else {
        return usbHandler.getKvDevice(deviceIndex - 1);
      }
    }
    else {
      return usbHandler.getKvDevice(deviceIndex);
    }
  }
  
  /**
   * Returns a KvDevice object which represents one physical Kvaser device.
   *
   * @param deviceEan Specifies which Ean the returned device shall have.
   * @return The first found device with the specified Ean, or null if there is no device that is
   * matching the requested Ean.
   */
  public KvDevice getDevice(Ean deviceEan) {
    KvDevice device;
    for (int i = 0; i < usbHandler.getNumberOfDevices(); i++) {
      device = usbHandler.getKvDevice(i);
      if (deviceEan.equals(device.getEan())) {
        return device;
      }
    }
    return null;
  }
  
  /**
   * Returns a KvDevice object which represents one physical Kvaser device.
   *
   * @param deviceEan    Specifies which Ean the returned device shall have.
   * @param serialNumber Specifies which serial number the returned device shall have.
   * @return The first found device with the specified Ean and serial number, or null if there is no
   * device that is matching the requested identification data.
   */
  public KvDevice getDevice(Ean deviceEan, int serialNumber) {
    KvDevice device;
    for (int i = 0; i < usbHandler.getNumberOfDevices(); i++) {
      device = usbHandler.getKvDevice(i);
      if (deviceEan.equals(device.getEan()) && (serialNumber == device.getSerialNumber())) {
        return device;
      }
    }
    return null;
  }
}
