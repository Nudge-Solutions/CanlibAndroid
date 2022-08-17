package com.kvaser.canlibtest;

import org.junit.*;
import android.support.test.*;
import android.content.*;

import static org.junit.Assert.*;

import com.kvaser.canlib.*;

/**
 * Test class for performance testing CanLib.
 *
 * This test assumes that one Kvaser Memorator Pro is connected to the target device and that the
 * USB permission prompt is accepted if shown when the test is run.
 *
 * The test configures a listener on channel 0, which sends a CAN message on channel 1 for every
 * message received. The listener also increments a counter. The test finishes when the counter
 * reaches NUMBER_OF_MESSAGES_TO_REPLY_TO. Typically, the test is used with a separate device
 * generating traffic on channel 0 by using Kvaser CanKing.
 *
 * The channels are set up to operate at 1000000 b/s.
 */
public class PerformanceTest {

  private static final int NUMBER_OF_MESSAGES_TO_REPLY_TO = 1000000;

  private static CanLib canLib;
  private static Context context;
  private static KvDevice device;
  private static KvChannel channel0, channel1;
  private static int messageCounter;

  @BeforeClass
  public static void testSetup() {
    messageCounter = 0;
    context = InstrumentationRegistry.getContext();
    canLib = CanLib.getInstance(context);
    canLib.setVirtualDeviceState(false);
    device = canLib.getDevice(0);
    try {
      channel0 = device.openChannel(0, null);
      channel1 = device.openChannel(1, null);
    } catch (CanLibException e) {
      e.printStackTrace();
      fail("Unexpected CanLibException");
    }
  }

  @Test
  public void setUpTestAndCountMessages() {
    try {
      channel0.registerCanMessageListener(new messageListener());
      channel0.busOn();
      channel0.setBusParams(new CanBusParams(1000000, 5, 2, 1));
      channel1.busOn();
      channel1.setBusParams(new CanBusParams(1000000, 5, 2, 1));
    } catch (NullPointerException | CanLibException e) {
      e.printStackTrace();
      fail("Unexpected exception");
    }

    while (messageCounter < NUMBER_OF_MESSAGES_TO_REPLY_TO) {
    }
  }

  private class messageListener implements CanMessageListener {

    public void canMessageReceived(CanMessage msg) {
      if (!msg.isFlagSet(CanMessage.MessageFlags.TX_ACK)) {
        messageCounter++;
        try {
          channel1.write(new CanMessage(100, 8, new byte[] {0, 1, 2, 3, 4, 5, 6, 7}));
        } catch (CanLibException e) {
          e.printStackTrace();
          fail("Unexpected CanLibException");
        }
      }
    }
  }
}
