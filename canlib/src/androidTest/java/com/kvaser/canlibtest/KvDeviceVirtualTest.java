package com.kvaser.canlibtest;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import android.content.*;
import android.support.test.*;
import com.kvaser.canlib.*;
import org.junit.*;
import org.junit.rules.*;

import java.util.*;

/**
 * Test class for KvDevice
 *
 * This test suit tests CanLib's virtual device.
 */
public class KvDeviceVirtualTest {

  @Rule
  public final ExpectedException exception = ExpectedException.none();
  private static KvDevice device;

  @BeforeClass
  public static void testSetup() {
    Context context = InstrumentationRegistry.getContext();
    CanLib canLib = CanLib.getInstance(context);
    device = canLib.getDevice(0);
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
  public void getSerialNumberShouldBeZero() {
    assertThat(device.getSerialNumber(), is(equalTo(0)));
  }

  @Test
  public void getNumberOfChannelsShouldBeTwo() {
    assertThat(device.getNumberOfChannels(), is(equalTo(2)));
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
      KvChannel channel;
      channel = device.openChannel(0, null);
      assertThat(channel, is(notNullValue()));
      channel.close();
      channel = device.openChannel(1, null);
      assertThat(channel, is(notNullValue()));
      channel.close();
    } catch (CanLibException e) {
      e.printStackTrace();
      fail("Unexpected CanLibException");
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
