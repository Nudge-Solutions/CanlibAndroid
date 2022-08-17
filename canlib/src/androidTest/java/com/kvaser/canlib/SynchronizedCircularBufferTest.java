package com.kvaser.canlib;

import junit.framework.TestCase;
/**
 * Created by davidj on 2015-11-18.
 */
public class SynchronizedCircularBufferTest extends TestCase {

  public void testAddPop1() {
    Byte b = 5;
    SynchronizedCircularBuffer buffer = new SynchronizedCircularBuffer(10, true);
    buffer.add(b);
    assertEquals("Wrong value popped." , b, buffer.pop());
    assertNull("Buffer expected to be empty.", buffer.pop());
  }

  public void testWrapOnOverflow() {
    Byte b = 5;
    SynchronizedCircularBuffer buffer = new SynchronizedCircularBuffer(4, true);
    for (int i = 0; i <= 5; i++)
    {
      buffer.add((byte)i);
    }
    for (int i = 2; i <= 5; i++)
    {
      assertEquals("Wrong value popped.", (byte) i, (byte) buffer.pop());
    }
    assertNull("Buffer expected to be empty.", buffer.pop());
  }

  public void testNoWrapOnOverflow() {
    Byte b = 5;
    SynchronizedCircularBuffer buffer = new SynchronizedCircularBuffer(4, false);
    for (int i = 0; i <= 5; i++)
    {
      buffer.add((byte)i);
    }
    for (int i = 0; i <= 3; i++)
    {
      assertEquals("Wrong value popped.", (byte) i, (byte) buffer.pop());
    }
    assertNull("Buffer expected to be empty.", buffer.pop());
  }
}