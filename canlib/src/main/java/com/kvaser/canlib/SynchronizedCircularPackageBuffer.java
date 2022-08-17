package com.kvaser.canlib;

/**
 * This class implements a thread-safe circular package buffer.
 */
class SynchronizedCircularPackageBuffer {
  private final byte[][] buffer;
  private int head, tail;
  private final int bufferSize;

  SynchronizedCircularPackageBuffer() {
    // The maximum size of an usb package is 512 bytes and the messages are less or equal to 32.
    // I.e. allocating a queue that can hold 10 usb worst case packages is likely to be sufficient
    this.bufferSize = (10 * 512 / 32) + 1;
    buffer = new byte[this.bufferSize][];
    head = 0;
    tail = 0;
  }

  synchronized void add(byte[] b) {
    if ((head + 1) % bufferSize != tail) {
      buffer[head] = b;
      head = (head + 1) % bufferSize;
    }
  }

  synchronized byte[] pop() {
    if (head != tail) {
      byte[] b = buffer[tail];
      tail = (tail + 1) % bufferSize;
      return b;
    } else {
      return null;
    }
  }

  synchronized byte peakSize() {
    if (head != tail) {
      return buffer[tail][0];
    } else {
      return 0;
    }
  }
}
