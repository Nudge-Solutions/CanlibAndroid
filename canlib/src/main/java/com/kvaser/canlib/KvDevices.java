package com.kvaser.canlib;

import android.hardware.usb.*;
import com.kvaser.canlib.CanLibException.ErrorDetail;
import com.kvaser.canlib.CanLibException.ErrorCode;

/**
 * Enumeration containing the mapping of Kvaser devices to drivers and product IDs.
 */
enum KvDevices {
  EAGLE(0x100, "KCany", "Kvaser Eagle"),
  BLACKBIRD_V2(0x102, "KCany", "Kvaser BlackBird v2"),
  MEMO_PRO_5HS(0x104, "KCany", "Kvaser Memorator Pro 5xHS"),
  USBCAN_PRO_5HS(0x105, "KCany", "Kvaser USBcan Pro 5xHS"),
  USBCAN_LIGHT_4HS(0x106, "KCany", "Kvaser USBcan Light 4xHS"),
  LEAF_PRO_HS_V2(0x107, "KCany", "Kvaser Leaf Pro HS v2"),
  USBCAN_PRO_2HS_V2(0x108, "KCany", "Kvaser USBcan Pro 2xHS v2"),
  MEMO_2HS_V2(0x109, "KCany", "Kvaser Memorator 2xHS v2"),
  MEMO_PRO_2HS_V2(0x10A, "KCany", "Kvaser Memorator Pro 2xHS v2"),
  
  LEAF_SEMIPRO_HS(0x00E, "KCanl", "Kvaser Leaf SemiPro HS"),
  MEMO_PRO_HS_HS(0x017, "KCanl", "Kvaser Memorator Professional"),
  LEAF_LIGHT_V2(0x120, "KCanl", "Kvaser Leaf Light v2"),
  LEAF_LIGHT_R_V2(0x127, "KCanl", "Kvaser Leaf Light R v2");

  private final int productId;
  private final String driverName;
  private final String deviceName;

  KvDevices(int productId, String driverName, String deviceName) {
    this.productId = productId;
    this.driverName = driverName;
    this.deviceName = deviceName;
  }

  /**
   * Checks if the product ID describes a product which is supported by a device driver.
   *
   * @param productId Product ID.
   * @return Returns true if the product is supported and null if not.
   */
  public static boolean isProductSupported(int productId) {
    for (KvDevices device : KvDevices.values()) {
      if (productId == device.getProductId() && !device.driverName.equals("")) {
        return true;
      }
    }
    return false;
  }

  /**
   * Creates a new device driver implementing KvDeviceInterface if a matching driver can be found.
   *
   * @param productId Product ID.
   * @param dev       The device connection that is used to send and receive through.
   * @param in        The endpoint for receiving data.
   * @param out       The endpoint for sending data.
   * @return Returns a new device driver if the product ID is supported.
   * @throws CanLibException if the product ID is not supported or if the driver throws exception
   *                         when initializing device.
   */
  public static KvDeviceInterface getDeviceInterface(int productId, UsbDeviceConnection dev, UsbEndpoint in, UsbEndpoint out) throws CanLibException {
    for (KvDevices device : KvDevices.values()) {
      if (productId == device.getProductId()) {
        switch (device.getDriverName()) {
          case "KCany":
            return new KCany(new UsbDeviceHandle(dev, in, out), out.getMaxPacketSize(), device);
          case "KCanl":
            return new KCanl(new UsbCanlDeviceHandle(dev, in, out), device);
        }
      }
    }
    throw new CanLibException(ErrorCode.ERR_DEVICE, ErrorDetail.NOT_SUPPORTED);
  }

  private int getProductId() {
    return productId;
  }

  private String getDriverName() {
    return driverName;
  }

  public String getDeviceName() {
    return deviceName;
  }

}
