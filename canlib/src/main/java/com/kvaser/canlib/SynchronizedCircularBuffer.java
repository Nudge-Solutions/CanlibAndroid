package com.kvaser.canlib;

/**
 * This class implements a thread-safe circular buffer.
 */
class SynchronizedCircularBuffer {
  private Byte[] buffer;
  private int head, tail, bufferSize;
  private boolean overwriteOnFull;

  SynchronizedCircularBuffer(int bufferSize, boolean overwriteOnFull) {
    buffer = new Byte[bufferSize + 1];
    this.bufferSize = bufferSize + 1;
    head = 0;
    tail = 0;
    this.overwriteOnFull = overwriteOnFull;
  }

  synchronized void add(Byte b) {
    if ((head + 1) % bufferSize != tail) {
      buffer[head] = b;
      head = (head + 1) % bufferSize;
    } else if (overwriteOnFull) {
      buffer[head] = b;
      head = (head + 1) % bufferSize;
      tail = (tail + 1) % bufferSize;
    }
  }

  synchronized Byte pop() {
    Byte b;
    if (head != tail) {
      b = buffer[tail];
      tail = (tail + 1) % bufferSize;
      return b;
    } else {
      return null;
    }
  }
}
