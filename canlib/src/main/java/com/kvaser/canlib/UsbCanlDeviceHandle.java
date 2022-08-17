package com.kvaser.canlib;

import android.hardware.usb.*;

import java.util.*;

/**
 * This class handles one USB device by sending and receiving through a device connection and its
 * endpoints.
 */

class UsbCanlDeviceHandle {

  private final List<UsbListener> usbListeners = new ArrayList<>();
  private final SynchronizedCircularPackageBuffer sendBuffer;
  private final Thread sendThread, receiveThread;
  private final UsbDeviceConnection deviceConnection;

  /**
   * The constructor requires handles to the device the class instance should handle.
   *
   * @param deviceConnection The device connection that is used to send and receive through.
   * @param inEndpoint       The endpoint for receiving data.
   * @param outEndpoint      The endpoint for sending data.
   */
  public UsbCanlDeviceHandle(UsbDeviceConnection deviceConnection, UsbEndpoint inEndpoint,
                             UsbEndpoint outEndpoint) {
    this.deviceConnection = deviceConnection;

    sendBuffer = new SynchronizedCircularPackageBuffer();
    receiveThread = new Thread(new ReceiveRunnable(inEndpoint, deviceConnection));
    sendThread = new Thread(new SendRunnable(outEndpoint, deviceConnection, sendBuffer));
    receiveThread.start();
    sendThread.start();
  }

  /**
   * Closes the device handle threads, including the device connection.
   */
  public void close() {
    sendThread.interrupt();
    receiveThread.interrupt();
    deviceConnection.close();
  }

  /**
   * Adds a listener which is called when data has been received.
   *
   * @param usbListener The listener to register.
   */
  public void addListener(UsbListener usbListener) {
    usbListeners.add(usbListener);
  }

  /**
   * Adds the supplied bytes to the send buffer.
   * Note that the bytes will be sent as a bulk transfer after a call to the this method, so the
   * supplied bytes should normally constitute exactly one command.
   *
   * @param bytes The bytes to be sent.
   */
  public void send(byte[] bytes) {
    if (bytes.length != 0) {
      sendBuffer.add(bytes);
    }
  }

  /**
   * This class provides a run method which blocks until there is data in the send buffer to send,
   * in which case it is transferred over the USB device connection. Should run in a separate
   * thread.
   */
  private class SendRunnable implements Runnable {

    private final SynchronizedCircularPackageBuffer sendBuffer;
    private final UsbEndpoint endpoint;
    private final UsbDeviceConnection deviceConnection;
    private final int usbTransferBlockSize;

    SendRunnable(UsbEndpoint endpoint, UsbDeviceConnection deviceConnection,
                 SynchronizedCircularPackageBuffer sendBuffer) {
      this.endpoint = endpoint;
      this.deviceConnection = deviceConnection;
      this.sendBuffer = sendBuffer;
      this.usbTransferBlockSize = endpoint.getMaxPacketSize();
    }

    public void run() {
      byte[] bytes = new byte[usbTransferBlockSize];
      int count = 0;
      while (!Thread.interrupted()) {
        while (sendBuffer.peakSize() != 0 && (usbTransferBlockSize - count) > sendBuffer.peakSize()) {
          byte[] b = sendBuffer.pop();
          if (b != null) {
            System.arraycopy(b, 0, bytes, count, b[0]);
            count += b[0];
          }
        }
        if (count != 0) {
          deviceConnection.bulkTransfer(endpoint, bytes, count, 1000);
          Arrays.fill(bytes, (byte) 0);
          count = 0;
        }
      }
    }
  }

  /**
   * This class provides a run method which blocks until data has been received over the USB device
   * connection, in which case a UsbListener event is generated. Should run in a separate thread.
   */
  private class ReceiveRunnable implements Runnable {

    private final UsbEndpoint endpoint;
    private final UsbDeviceConnection deviceConnection;
    private final int usbTransferBlockSize;
    private final byte[] bytes;

    ReceiveRunnable(UsbEndpoint endpoint, UsbDeviceConnection deviceConnection) {
      this.endpoint = endpoint;
      this.deviceConnection = deviceConnection;
      this.usbTransferBlockSize = endpoint.getMaxPacketSize();
      bytes = new byte[this.usbTransferBlockSize];
    }

    public void run() {
      while (!Thread.interrupted()) {
        int receivedLength = deviceConnection.bulkTransfer(endpoint, bytes, bytes.length, 1000);
        if (receivedLength > 0) {
          // Since bulk transfer is used it should be safe to assume that what we get here is one or
          // more complete packages so process them one by one.
          int consumedBytes = 0;
          while (receivedLength - consumedBytes >= 3 && bytes[consumedBytes] != 0) {
            // Call listeners
            byte[] packet = Arrays.copyOfRange(bytes, consumedBytes, consumedBytes + bytes[consumedBytes]);
            consumedBytes += bytes[consumedBytes];
            for (UsbListener l : usbListeners) {
              l.UsbDataReceived(packet);
            }
          }
        }
      }
    }
  }
}
