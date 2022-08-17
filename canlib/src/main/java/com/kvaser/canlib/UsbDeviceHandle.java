package com.kvaser.canlib;

import android.hardware.usb.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * This class handles one USB device by sending and receiving through a device connection and its
 * endpoints.
 */
class UsbDeviceHandle {

  private static final int usbTransferBlockSize = 32;
  private static final int usbBufferSize = 8192;

  private List<UsbListener> usbListeners = new ArrayList<>();
  private SynchronizedCircularBuffer sendBuffer;
  private final Semaphore emptyLock = new Semaphore(0);
  private Thread sendThread, receiveThread;
  private UsbDeviceConnection deviceConnection;

  /**
   * The constructor requires handles to the device the class instance should handle.
   *
   * @param deviceConnection The device connection that is used to send and receive through.
   * @param inEndpoint       The endpoint for receiving data.
   * @param outEndpoint      The endpoint for sending data.
   */
  public UsbDeviceHandle(UsbDeviceConnection deviceConnection, UsbEndpoint inEndpoint,
                         UsbEndpoint outEndpoint) {
    this.deviceConnection = deviceConnection;
    sendBuffer = new SynchronizedCircularBuffer(usbBufferSize, false);
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
    for (byte b : bytes) {
      sendBuffer.add(b);
      emptyLock.release();
    }
  }

  /**
   * This class provides a run method which blocks until there is data in the send buffer to send,
   * in which case it is transferred over the USB device connection. Should run in a separate
   * thread.
   */
  private class SendRunnable implements Runnable {

    SynchronizedCircularBuffer sendBuffer;
    private UsbEndpoint endpoint;
    private UsbDeviceConnection deviceConnection;
    private byte[] bytes = new byte[usbTransferBlockSize];

    SendRunnable(UsbEndpoint endpoint, UsbDeviceConnection deviceConnection,
                 SynchronizedCircularBuffer sendBuffer) {
      this.endpoint = endpoint;
      this.deviceConnection = deviceConnection;
      this.sendBuffer = sendBuffer;
    }

    public void run() {
      Byte b;
      try {
        while (!Thread.interrupted()) {
          for (int i = 0; i < bytes.length; i++) {
            emptyLock.acquire();
            b = sendBuffer.pop();
            if (b != null) {
              bytes[i] = b;
            } else {
              i--;
            }
          }
          deviceConnection.bulkTransfer(endpoint, bytes, bytes.length, 1000);
        }
      } catch (InterruptedException e) {
        // Exit thread if interrupted
      }
    }
  }

  /**
   * This class provides a run method which blocks until data has been received over the USB device
   * connection, in which case a UsbListener event is generated. Should run in a separate thread.
   */
  private class ReceiveRunnable implements Runnable {

    private UsbEndpoint endpoint;
    private UsbDeviceConnection deviceConnection;
    private byte[] bytes = new byte[usbBufferSize];

    ReceiveRunnable(UsbEndpoint endpoint, UsbDeviceConnection deviceConnection) {
      this.endpoint = endpoint;
      this.deviceConnection = deviceConnection;
    }

    public void run() {
      while (!Thread.interrupted()) {
        int receivedLength = deviceConnection.bulkTransfer(endpoint, bytes, bytes.length, 1000);
        if (receivedLength > 0) {
          // Call listeners
          for (UsbListener l : usbListeners) {
            l.UsbDataReceived(Arrays.copyOf(bytes, receivedLength));
          }
        }
      }
    }
  }
}
