package com.kvaser.canlib;

import android.app.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.*;
import android.os.*;
import android.support.v4.util.*;

import java.util.*;

/**
 * Handles the Android USB Host library. Indexes devices and creates UsbDeviceHandles.
 */
class UsbHandler {

  private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
  private static final int KvaserVendorId = 0x0bfd;
  // Lock for synchronizing send operations
  private final Object permissionWaitLock = new Object();
  private final Context context;
  private final UsbManager usbManager;
  private final BroadcastReceiver mUsbAttachedReceiver = new BroadcastReceiver() {
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();

      if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (device != null) {
          /* Ask permission to use the attached USB device */
          checkDevicePermission(context, device);
        }
      }
    }
  };
  // Map of connected USB devices to their driver
  private final SimpleArrayMap<UsbDevice, KvDeviceInterface> driverMap =
      new SimpleArrayMap<>();
  // List of active drivers
  private final List<KvDeviceInterface> deviceDrivers = new ArrayList<>();
  private final BroadcastReceiver mUsbDetachedReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (device != null) {
          // Remove the corresponding device driver from the map and driver list and close it
          KvDeviceInterface driver = driverMap.remove(device);
          if (driver != null) {
            deviceDrivers.remove(driver);
            driver.close();
          }
        }
      }
    }
  };
  private final BroadcastReceiver mUsbPermissionReceiver = new BroadcastReceiver() {

    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (ACTION_USB_PERMISSION.equals(action)) {
        try {
          synchronized (this) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
              if (device != null) {
                setupUsbDevice(device);
              }
            }
          }
        } catch (CanLibException e) {
          e.printStackTrace();
        }
        // Notify that the permission dialog has been closed
        synchronized (permissionWaitLock) {
          permissionWaitLock.notifyAll();
        }
      }
    }
  };

  /**
   * Gets a list of currently attached devices from the system's UsbManager and iterates it to find
   * a device with Kvaser's vendor ID.
   */
  UsbHandler(Context context) {
    this.context = context;
    usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

    HandlerThread handlerThread = new HandlerThread("ReceiverThread");
    handlerThread.start();
    Looper looper = handlerThread.getLooper();

    IntentFilter permissionFilter = new IntentFilter();
    permissionFilter.addAction(ACTION_USB_PERMISSION);
    context.registerReceiver(mUsbPermissionReceiver, permissionFilter, null, new Handler(looper));

    IntentFilter attachedFilter = new IntentFilter();
    attachedFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
    context.registerReceiver(mUsbAttachedReceiver, attachedFilter);

    IntentFilter detachedFilter = new IntentFilter();
    detachedFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
    context.registerReceiver(mUsbDetachedReceiver, detachedFilter);

    HashMap<String, UsbDevice> deviceList = this.usbManager.getDeviceList();
    Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
    UsbDevice device;
    while (deviceIterator.hasNext()) {
      device = deviceIterator.next();
      checkDevicePermission(context, device);
      synchronized (permissionWaitLock) {
        try {
          permissionWaitLock.wait();
        } catch (InterruptedException e) {
          // Try to set up the next device or exit constructor
        }
      }
    }
  }

  public int getNumberOfDevices() {
    return deviceDrivers.size();
  }

  public KvDevice getKvDevice(int deviceIndex) {
    if (deviceIndex >= 0 && deviceIndex < deviceDrivers.size()) {
      return new KvDevice(deviceDrivers.get(deviceIndex));
    } else {
      return null;
    }
  }

  /*
   * Check a connected USB device if it is supported and ask for permission to access it.
   *
   * @param context The application context.
   * @param device  The USB device to check.
   */
  private void checkDevicePermission(Context context, UsbDevice device) {
    if (device.getVendorId() == KvaserVendorId) {
      if (KvDevices.isProductSupported(device.getProductId())) {

        PendingIntent mPermissionIntent = PendingIntent.getBroadcast(context, 0,
                                                                     new android.content.Intent(
                                                                         ACTION_USB_PERMISSION), 0);
        usbManager.requestPermission(device, mPermissionIntent);
      }
    }
  }

  /**
   * Returns the driver associated with a certain channel.
   *
   * @param channelIndex The channel which is associated with the device.
   * @return Returns a device driver if one could be found and null if not.
   */
  public KvDeviceInterface findDriver(int channelIndex) {

    int deviceIndex = 0;
    int totalChannels = 0;
    while (deviceIndex < deviceDrivers.size() && channelIndex >= totalChannels + deviceDrivers
        .get(deviceIndex).getNumberOfChannels()) {
      totalChannels += deviceDrivers.get(deviceIndex).getNumberOfChannels();
      deviceIndex++;
    }

    if (deviceIndex < deviceDrivers.size()) {
      return deviceDrivers.get(deviceIndex);
    } else {
      return null;
    }
  }

  /*
   * Create a driver for the device and add it to the driver list. Find the correct interfaces and
   * endpoints for the device and open a connection.
   *
   * @param device The UsbDevice to set up.
   */
  private void setupUsbDevice(UsbDevice device) throws CanLibException {
    UsbEndpoint inEndpoint = null;
    UsbEndpoint outEndpoint = null;
    UsbInterface usbInterface = null;

    // Loop to find the correct interface
    for (int i = 0; i < device.getInterfaceCount(); i++) {
      if (device.getInterface(i).getInterfaceClass() == UsbConstants.USB_CLASS_PER_INTERFACE) {
        usbInterface = device.getInterface(i);

        // Loop to find the input and output bulk transfer endpoints
        for (int j = 0; j < usbInterface.getEndpointCount(); j++) {
          UsbEndpoint currentEndpoint = usbInterface.getEndpoint(j);
          if (inEndpoint == null && ((currentEndpoint.getDirection() == UsbConstants.USB_DIR_IN)
              && (((currentEndpoint.getAttributes() & UsbConstants.USB_ENDPOINT_XFERTYPE_MASK)
                    == UsbConstants.USB_ENDPOINT_XFER_BULK))))
          {
            inEndpoint = currentEndpoint;
          }

          if (outEndpoint == null && ((usbInterface.getEndpoint(j).getDirection() == UsbConstants.USB_DIR_OUT) &&
              (((usbInterface.getEndpoint(j).getAttributes() & UsbConstants.USB_ENDPOINT_XFERTYPE_MASK)
               == UsbConstants.USB_ENDPOINT_XFER_BULK) ))) {
            outEndpoint = currentEndpoint;
          }

          if(inEndpoint != null && outEndpoint != null) break;
        }
      }
    }

    if (usbInterface != null && inEndpoint != null && outEndpoint != null) {
      // Open a connection to the device and claim the interface
      UsbDeviceConnection deviceConnection = usbManager.openDevice(device);
      if (deviceConnection != null) {
        deviceConnection.claimInterface(usbInterface, true);

        KvDeviceInterface driver;
        driver = KvDevices.getDeviceInterface(device.getProductId(), deviceConnection, inEndpoint, outEndpoint);
        deviceDrivers.add(driver);
        driverMap.put(device, driver);
      }
    }
  }

  public void close() {
    // Unregister receivers if they are registered
    try {
      context.unregisterReceiver(mUsbDetachedReceiver);
    } catch (IllegalArgumentException e) {
      // Do nothing if the receiver was not registered
    }
    try {
      context.unregisterReceiver(mUsbAttachedReceiver);
    } catch (IllegalArgumentException e) {
      // Do nothing if the receiver was not registered
    }
    try {
      context.unregisterReceiver(mUsbPermissionReceiver);
    } catch (IllegalArgumentException e) {
      // Do nothing if the receiver was not registered
    }

    for (int i = 0; i < driverMap.size(); i++) {
      // Remove the corresponding device driver from the map and driver list and close it
      KvDeviceInterface driver = driverMap.removeAt(i);
      if (driver != null) {
        deviceDrivers.remove(driver);
        driver.close();
      }
    }
  }
}
