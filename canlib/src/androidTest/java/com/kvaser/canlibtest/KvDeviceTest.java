package com.kvaser.canlibtest;

import org.junit.*;
import org.junit.rules.*;
import android.os.*;
import android.support.test.*;
import android.content.*;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

import com.kvaser.canlib.*;

import java.util.*;

/**
 * Test class for KvDevice
 *
 * This test assumes that one Kvaser Memorator Pro is connected to the target device and that the
 * USB permission prompt is accepted if shown when the test is run.
 *
 * The Memorator's LEDs should flash during the test.
 */
public class KvDeviceTest {

  @Rule
  public final ExpectedException exception = ExpectedException.none();
  private static KvDevice device;

  @BeforeClass
  public static void testSetup() {
    Context context = InstrumentationRegistry.getContext();
    CanLib canLib = CanLib.getInstance(context);
    device = canLib.getDevice(1); //Index 0 is the virtual device
    assertThat(device, is(notNullValue()));
  }

  @Test
  public void getDeviceInfoShouldNotReturnNull() {
    assertThat(device.getDeviceInfo(), is(notNullValue()));
  }

  @Test
  public void getEanShouldNotReturnNull() {
    assertThat(device.getEan(), is(notNullValue()));
  }

  @Test
  public void getSerialNumberShouldBeGreaterThanZero() {
    assertThat(device.getSerialNumber(), is(greaterThan(0)));
  }

  @Test
  public void getNumberOfChannelsReturnsTheExpectedValue() {
    Bundle bundle = device.getDeviceInfo();
    String ean = (String) bundle.get("Card EAN");
    ean = ean.replaceAll("\\s+",""); // Remove whitespace
    if (ean.equals("73-30130-00351-4")) {
      assertThat(device.getNumberOfChannels(), is(equalTo(2)));
    }
    else {
      // Assume that the correct hydra device is connected
      assertThat(device.getNumberOfChannels(), is(equalTo(5)));
    }
  }

  @Test
  public void flashLeds() {
    device.flashLeds();
  }

  @Test
  public void openChannelWithNegativeIndexShouldThrowException() throws CanLibException {
    exception.expect(CanLibException.class);
    device.openChannel(-1, null);
  }

  @Test
  public void openChannelWithTooHighIndexShouldThrowException() throws CanLibException {
    exception.expect(CanLibException.class);
    device.openChannel(5, null);
  }

  @Test
  public void openChannelWithValidIndex() {
    try {
      int noOfChannels = device.getNumberOfChannels();

      for (int ch = 0; ch < noOfChannels; ch++) {
        KvChannel channel;
        channel = device.openChannel(ch, null);
        assertThat(channel, is(notNullValue()));
        channel.close();
      }
    } catch (CanLibException e) {
      e.printStackTrace();
      fail("Unexpected CanLibException " + e.getMessage());
    }
  }

  @Test
  public void openChannelWithFlags() {
    try {
      KvChannel channel = device.openChannel(0, EnumSet.of(KvChannel.ChannelFlags.ACCEPT_LARGE_DLC,
                                                           KvChannel.ChannelFlags.EXCLUSIVE,
                                                           KvChannel.ChannelFlags.REQUIRE_EXTENDED));
      assertThat(channel, is(notNullValue()));
      channel.close();
    } catch (CanLibException e) {
      e.printStackTrace();
      fail("Unexpected CanLibException");
    }
  }

  @Test
  public void openAlreadyOpenedChannelAsExclusiveShouldThrowException() throws CanLibException {
    KvChannel channel = null;
    try {
      channel = device.openChannel(0, null);
      assertThat(channel, is(notNullValue()));
    } catch (CanLibException e) {
      e.printStackTrace();
      fail("Unexpected CanLibException");
    }
    exception.expect(CanLibException.class);
    KvChannel exclusiveChannel =
        device.openChannel(0, EnumSet.of(KvChannel.ChannelFlags.EXCLUSIVE));
    assertThat(exclusiveChannel, is(nullValue()));
    channel.close();
  }

  @Test
  public void openExclusivelyOpenedChannelShouldThrowException() throws CanLibException {
    KvChannel exclusiveChannel = null;
    try {
      exclusiveChannel = device.openChannel(1, EnumSet.of(KvChannel.ChannelFlags.EXCLUSIVE));
      assertThat(exclusiveChannel, is(notNullValue()));
    } catch (CanLibException e) {
      e.printStackTrace();
      fail("Unexpected CanLibException");
    }
    exception.expect(CanLibException.class);
    try {
      device.openChannel(1, null);
    } catch (CanLibException e) {
      exclusiveChannel.close();
      throw e;
    }
  }
}
