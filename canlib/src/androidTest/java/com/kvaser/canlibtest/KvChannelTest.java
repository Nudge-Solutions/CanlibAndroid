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
 * This test assumes that one Kvaser Memorator Pro is connected to the target device and that the
 * USB permission prompt is accepted if shown when the test is run. Furthermore, it is assumed that
 * channel 0 and 1 are connected.
 */
public class KvChannelTest {

  private static KvDevice device;
  private static KvChannel channel0, channel1;
  private static ChipState chipState;
  private static boolean eventWasTriggered = false;
  private static CanMessage receivedMessage = null;
  private static boolean messageWasReceived = false;
  @Rule
  public final ExpectedException exception = ExpectedException.none();
  private final Object waitLockMsg = new Object();
  private final Object waitLockState = new Object();

  @BeforeClass
  public static void testSetup() {
    Context context = InstrumentationRegistry.getContext();
    try {
      CanLib canLib = CanLib.getInstance(context);
      device = canLib.getDevice(1); // Index 0 is the virtual device
      channel0 = device.openChannel(0, null);
      channel1 = device.openChannel(1, null);
    } catch (CanLibException e) {
      e.printStackTrace();
      fail("Unexpected CanLibException");
    }
    assertThat(channel0, is(notNullValue()));
    assertThat(channel1, is(notNullValue()));
    try {
      channel0.busOn();
      channel1.busOn();
      channel0.busOff();
      channel1.busOff();
    } catch (CanLibException e) {
      e.printStackTrace();
      fail("Unexpected CanLibException");
    }
  }

  @Before
  public void beforeEachTest() {
    channel0.clearFilters();
    channel1.clearFilters();
  }

  @After
  public void afterEachTest() {
    try {
      channel0.busOn();
      channel1.busOn();
      channel0.busOff();
      channel1.busOff();
    } catch (CanLibException e) {
      e.printStackTrace();
      fail("Unexpected CanLibException");
    }
  }

  @Test
  public void getDeviceNotNull() {
    assertThat(channel0.getDevice(), is(notNullValue()));
  }

  @Test
  public void getDeviceInfoNotNull() {
    assertThat(channel0.getDeviceInfo(), is(notNullValue()));
  }

  @Test
  public void getBusParamsNotNull() {
    try {
      assertThat(channel0.getBusParams(), is(notNullValue()));
    } catch (CanLibException e) {
      e.printStackTrace();
      fail("Unexpected CanLibException");
    }
  }

  @Test
  public void setBusParamsBitrateTooLow() throws CanLibException {
    CanBusParams params = new CanBusParams(0, 1, 1, 1);
    exception.expect(CanLibException.class);
    channel0.setBusParams(params);
  }

  @Test
  public void setBusParamsTseg1TooLow() throws CanLibException {
    CanBusParams params = new CanBusParams(1, 0, 1, 1);
    exception.expect(CanLibException.class);
    channel0.setBusParams(params);
  }

  @Test
  public void setBusParamsTseg1TooHigh() throws CanLibException {
    CanBusParams params = new CanBusParams(1, 17, 1, 1);
    exception.expect(CanLibException.class);
    channel0.setBusParams(params);
  }

  @Test
  public void setBusParamsTseg2TooLow() throws CanLibException {
    CanBusParams params = new CanBusParams(1, 1, 0, 1);
    exception.expect(CanLibException.class);
    channel0.setBusParams(params);
  }

  @Test
  public void setBusParamsTseg2TooHigh() throws CanLibException {
    CanBusParams params = new CanBusParams(1, 1, 9, 1);
    exception.expect(CanLibException.class);
    channel0.setBusParams(params);
  }

  @Test
  public void setBusParamsSjwTooLow() throws CanLibException {
    CanBusParams params = new CanBusParams(1, 1, 1, 0);
    exception.expect(CanLibException.class);
    channel0.setBusParams(params);
  }

  @Test
  public void setBusParamsSjwTooHigh() throws CanLibException {
    CanBusParams params = new CanBusParams(1, 1, 1, 5);
    exception.expect(CanLibException.class);
    channel0.setBusParams(params);
  }

  @Ignore //TODO Response to setBusParams() are not implemented in current Memorator firmware 3.0.546
  public void setAndGetValidBusParams() {
    CanBusParams params = new CanBusParams(1, 2, 5, 4);
    try {
      channel0.setBusParams(params);
      CanBusParams readParams = channel0.getBusParams();
      assertThat(readParams.bitRate, is(equalTo(params.bitRate)));
      assertThat(readParams.tseg1, is(equalTo(params.tseg1)));
      assertThat(readParams.tseg2, is(equalTo(params.tseg2)));
      assertThat(readParams.sjw, is(equalTo(params.sjw)));
    } catch (CanLibException e) {
      e.printStackTrace();
      fail("Unexpected CanLibException");
    }
  }

  @Test
  public void setBusOnAndExpectNoException() {
    try {
      channel0.busOn();
    } catch (CanLibException e) {
      e.printStackTrace();
      fail("Unexpected CanLibException");
    }
  }

  @Test
  public void setBusOffAndExpectNoException() {
    try {
      channel0.busOff();
    } catch (CanLibException e) {
      e.printStackTrace();
      fail("Unexpected CanLibException");
    }
  }

  @Test
  public void setBusOutputControlAndReadBack() {
    try {
      CanDriverType driverType = CanDriverType.SILENT;
      channel0.setBusOutputControl(driverType);
      assertThat(channel0.getBusOutputControl(), is(equalTo(CanDriverType.SILENT)));

      driverType = CanDriverType.NORMAL;
      channel0.setBusOutputControl(driverType);
      assertThat(channel0.getBusOutputControl(), is(equalTo(CanDriverType.NORMAL)));
    } catch (CanLibException e) {
      e.printStackTrace();
      fail("Unexpected CanLibException");
    }
  }

  @Test // TODO Memorator returns driver type 0 on first getBusOutputControl command after reset
  public void getBusOutputControlAndExpectNoExceptionOrNull() {
    try {
      assertThat(channel0.getBusOutputControl(), is(notNullValue()));
    } catch (CanLibException e) {
      e.printStackTrace();
      fail("Unexpected CanLibException");
    }
  }

  @Test
  public void setIllegalBusOutputControlAndExpectException() throws CanLibException {
    exception.expect(CanLibException.class);
    channel0.setBusOutputControl(CanDriverType.OFF);
  }

  @Test
  public void writeWithReceivingChannelOffAndExpectErrorFrame() {
    MessageListener listener = new MessageListener();
    synchronized (waitLockState) {
      try {
        channel0.busOn();
        channel1.busOff();
        channel0.registerCanMessageListener(listener);
        messageWasReceived = false;
        channel0.write(new CanMessage(10, 8, getRandomData()));
        waitLockState.wait(300);
        channel0.unregisterCanMessageListener(listener);
        assertThat(messageWasReceived, is(true));
        assertThat(receivedMessage.isFlagSet(CanMessage.MessageFlags.ERROR_FRAME), is(true));
      } catch (InterruptedException | CanLibException e) {
        e.printStackTrace();
        fail("Unexpected exception");
      }
    }
  }

  @Test
  public void checkThatChannel0ReceivedTxAck() {
    byte[] data = getRandomData();
    MessageListener listener = new MessageListener();
    CanMessage sendMessage = new CanMessage(10, 8, data);
    synchronized (waitLockMsg) {
      try {
        channel0.setBusParams(new CanBusParams(CanPredefinedBitRates.BITRATE_125K));
        channel1.setBusParams(new CanBusParams(CanPredefinedBitRates.BITRATE_125K));
        channel0.busOn();
        channel1.busOn();
        channel0.registerCanMessageListener(listener);
        messageWasReceived = false;
        channel0.write(sendMessage);
        waitLockMsg.wait(300);
        channel0.unregisterCanMessageListener(listener);
        assertThat(messageWasReceived, is(true));
        assertThat(receivedMessage.getDirection(), is(equalTo(CanMessage.Direction.TX)));
        assertThat(receivedMessage.id, is(equalTo(sendMessage.id)));
        assertThat(receivedMessage.dlc, is(equalTo(sendMessage.dlc)));
        assertThat(receivedMessage.data, is(equalTo(sendMessage.data)));
      } catch (InterruptedException | CanLibException e) {
        e.printStackTrace();
        fail("Unexpected exception");
      }
    }
  }

  @Test
  public void checkThatChannel1ReceivedMessage() {
    byte[] data = getRandomData();
    CanMessage sendMessage = new CanMessage(10, 8, data);
    sendMessage.setFlag(CanMessage.MessageFlags.STANDARD_ID);
    CanMessage compareMessage = new CanMessage(10, 8, data);
    compareMessage.setFlag(CanMessage.MessageFlags.STANDARD_ID);
    sendAndCompare(channel0, sendMessage, channel1, channel1, compareMessage,
                   CanMessage.Direction.RX);
  }

  @Test
  public void checkThatIllegalStandardIdThrowsException() throws CanLibException {
    try {
      channel0.setBusParams(new CanBusParams(CanPredefinedBitRates.BITRATE_125K));
      channel1.setBusParams(new CanBusParams(CanPredefinedBitRates.BITRATE_125K));
      channel0.busOn();
      channel1.busOn();
    } catch (CanLibException e) {
      e.printStackTrace();
      fail("Unexpected CanLibException");
    }
    CanMessage sendMessage = new CanMessage(0xFFF, 8, getRandomData());
    sendMessage.setFlag(CanMessage.MessageFlags.STANDARD_ID);
    exception.expect(CanLibException.class);
    channel0.write(sendMessage);
  }

  @Test
  public void checkThatIllegalExtendedIdThrowsException() throws CanLibException {
    try {
      channel0.setBusParams(new CanBusParams(CanPredefinedBitRates.BITRATE_125K));
      channel1.setBusParams(new CanBusParams(CanPredefinedBitRates.BITRATE_125K));
      channel0.busOn();
      channel1.busOn();
    } catch (CanLibException e) {
      e.printStackTrace();
      fail("Unexpected CanLibException");
    }
    CanMessage sendMessage = new CanMessage(0xFFFFFFFF, 8, getRandomData());
    sendMessage.setFlag(CanMessage.MessageFlags.EXTENDED_ID);
    exception.expect(CanLibException.class);
    channel0.write(sendMessage);
  }

  @Test
  public void checkThatStandardFlagIsSet() {
    byte[] data = getRandomData();
    CanMessage sendMessage = new CanMessage(10, 8, data);
    sendMessage.setFlag(CanMessage.MessageFlags.STANDARD_ID);
    CanMessage compareMessage = new CanMessage(10, 8, data);
    compareMessage.setFlag(CanMessage.MessageFlags.STANDARD_ID);
    sendAndCompare(channel0, sendMessage, channel1, channel1, compareMessage,
                   CanMessage.Direction.RX);
  }

  @Test
  public void checkThatExtendedFlagIsSet() {
    byte[] data = getRandomData();
    CanMessage sendMessage = new CanMessage(0xFFFFFF, 8, data);
    sendMessage.setFlag(CanMessage.MessageFlags.EXTENDED_ID);
    CanMessage compareMessage = new CanMessage(0xFFFFFF, 8, data);
    compareMessage.setFlag(CanMessage.MessageFlags.EXTENDED_ID);
    sendAndCompare(channel0, sendMessage, channel1, channel1, compareMessage,
                   CanMessage.Direction.RX);
  }

  @Test
  public void checkThatIllegalDlcThrowsException() throws CanLibException {
    try {
      channel0.setBusParams(new CanBusParams(CanPredefinedBitRates.BITRATE_125K));
      channel1.setBusParams(new CanBusParams(CanPredefinedBitRates.BITRATE_125K));
      channel0.busOn();
      channel1.busOn();
    } catch (CanLibException e) {
      e.printStackTrace();
      fail("Unexpected CanLibException");
    }
    CanMessage sendMessage = new CanMessage(10, 9, getRandomData());
    exception.expect(CanLibException.class);
    channel0.write(sendMessage);
  }

  @Test
  public void checkAcceptLargeDlcFlag() {
    try {
      KvChannel tempChannel =
          device.openChannel(0, EnumSet.of(KvChannel.ChannelFlags.ACCEPT_LARGE_DLC));
      tempChannel.setBusParams(new CanBusParams(CanPredefinedBitRates.BITRATE_125K));
      channel1.setBusParams(new CanBusParams(CanPredefinedBitRates.BITRATE_125K));
      tempChannel.busOn();
      channel1.busOn();
      CanMessage sendMessage = new CanMessage(10, 9, getRandomData());
      tempChannel.write(sendMessage);
    } catch (CanLibException e) {
      if (e.getErrorCode().equals(CanLibException.ErrorCode.ERR_PARAM) &&
          e.getErrorDetail().equals(CanLibException.ErrorDetail.ILLEGAL_DLC)) {
        fail("Unexpected illegal DLC exception");
      } else {
        e.printStackTrace();
        fail("Unexpected exception");
      }
    }
  }

  @Test
  public void checkRangePassFilter() {
    CanMessageFilter filter = new CanMessageFilter();
    filter.filterType = CanMessageFilter.FilterType.PASS;
    filter.filterMatchType = CanMessageFilter.FilterMatchType.RANGE;
    filter.idMin = 50;
    filter.idMax = 150;
    int[] idShouldPass = {50, 70, 81, 130, 150};
    int[] idShouldStop = {0, 49, 151, 0x1FF};
    checkFilter(filter, idShouldPass, idShouldStop, null, null);
  }

  @Test
  public void checkRangeStopFilter() {
    CanMessageFilter filter = new CanMessageFilter();
    filter.filterType = CanMessageFilter.FilterType.STOP;
    filter.filterMatchType = CanMessageFilter.FilterMatchType.RANGE;
    filter.idMin = 5;
    filter.idMax = 10;
    int[] idShouldPass = {0, 4, 11, 0x1FF};
    int[] idShouldStop = {5, 7, 10};
    checkFilter(filter, idShouldPass, idShouldStop, null, null);
  }

  @Test
  public void checkMaskPassFilter() {
    CanMessageFilter filter = new CanMessageFilter();
    filter.filterType = CanMessageFilter.FilterType.PASS;
    filter.filterMatchType = CanMessageFilter.FilterMatchType.MASK;
    filter.mask = 0x0F;
    filter.code = 0x05;
    int[] idShouldPass = {5, 0x185};
    int[] idShouldStop = {0, 2, 4, 0xF, 0x1FF};
    checkFilter(filter, idShouldPass, idShouldStop, null, null);
  }

  @Test
  public void checkMaskStopFilter() {
    CanMessageFilter filter = new CanMessageFilter();
    filter.filterType = CanMessageFilter.FilterType.STOP;
    filter.filterMatchType = CanMessageFilter.FilterMatchType.MASK;
    filter.mask = 0x0F;
    filter.code = 0x03;
    int[] idShouldPass = {0, 2, 4, 0xF, 0x1FF};
    int[] idShouldStop = {3, 0x183};
    checkFilter(filter, idShouldPass, idShouldStop, null, null);
  }

  @Test
  public void checkMaskAndRangePassFilter() {
    CanMessageFilter filter = new CanMessageFilter();
    filter.filterType = CanMessageFilter.FilterType.PASS;
    filter.filterMatchType = CanMessageFilter.FilterMatchType.MASK_AND_RANGE;
    filter.mask = 0x0F;
    filter.code = 0x03;
    filter.idMin = 2;
    filter.idMax = 0x1F;
    int[] idShouldPass = {3, 0x13};
    int[] idShouldStop = {2, 0x1F, 0xF3};
    checkFilter(filter, idShouldPass, idShouldStop, null, null);
  }

  @Test
  public void checkMaskAndRangeStopFilter() {
    CanMessageFilter filter = new CanMessageFilter();
    filter.filterType = CanMessageFilter.FilterType.STOP;
    filter.filterMatchType = CanMessageFilter.FilterMatchType.MASK_AND_RANGE;
    filter.mask = 0x0F;
    filter.code = 0x03;
    filter.idMin = 2;
    filter.idMax = 0x1F;
    int[] idShouldPass = {2, 0x1F, 0xF3};
    int[] idShouldStop = {3, 0x13};
    checkFilter(filter, idShouldPass, idShouldStop, null, null);
  }

  @Test
  public void checkMaskOrRangePassFilter() {
    CanMessageFilter filter = new CanMessageFilter();
    filter.filterType = CanMessageFilter.FilterType.PASS;
    filter.filterMatchType = CanMessageFilter.FilterMatchType.MASK_OR_RANGE;
    filter.mask = 0x0F;
    filter.code = 0x03;
    filter.idMin = 3;
    filter.idMax = 0x1F;
    int[] idShouldPass = {3, 4, 0x13, 0xF};
    int[] idShouldStop = {0x1FF, 2, 0};
    checkFilter(filter, idShouldPass, idShouldStop, null, null);
  }

  @Test
  public void checkMaskOrRangeStopFilter() {
    CanMessageFilter filter = new CanMessageFilter();
    filter.filterType = CanMessageFilter.FilterType.STOP;
    filter.filterMatchType = CanMessageFilter.FilterMatchType.MASK_OR_RANGE;
    filter.mask = 0x0F;
    filter.code = 0x03;
    filter.idMin = 3;
    filter.idMax = 0x1F;
    int[] idShouldPass = {0x1FF, 2, 0};
    int[] idShouldStop = {3, 4, 0x13, 0xF};
    checkFilter(filter, idShouldPass, idShouldStop, null, null);
  }

  @Test
  public void checkStandardIdMaskPassFilter() {
    CanMessageFilter filter = new CanMessageFilter();
    filter.filterIdType = CanMessageFilter.FilterIdType.STANDARD;
    filter.filterType = CanMessageFilter.FilterType.PASS;
    filter.filterMatchType = CanMessageFilter.FilterMatchType.MASK;
    filter.mask = 0x0F;
    filter.code = 0x03;
    int[] idShouldPass = {0x3, 0x13, 0x1F3};
    boolean[] isExtendedPass = {false, false, false};
    int[] idShouldStop = {0x3, 0x7FF, 0x8F3, 0x1FFFFFF3};
    boolean[] isExtendedStop = {true, true, true, true};
    checkFilter(filter, idShouldPass, idShouldStop, isExtendedPass, isExtendedStop);
  }

  @Test
  public void checkExtendedIdMaskPassFilter() {
    CanMessageFilter filter = new CanMessageFilter();
    filter.filterIdType = CanMessageFilter.FilterIdType.EXTENDED;
    filter.filterType = CanMessageFilter.FilterType.PASS;
    filter.filterMatchType = CanMessageFilter.FilterMatchType.MASK;
    filter.mask = 0x0F;
    filter.code = 0x03;
    int[] idShouldPass = {0x3, 0x13, 0x7F3, 0x8F3, 0x1FFFFFF3};
    boolean[] isExtendedPass = {true, true, true, true, true};
    int[] idShouldStop = {0x3, 0x13, 0x7F3, 0x1FFFFFFF};
    boolean[] isExtendedStop = {false, false, false, true};
    checkFilter(filter, idShouldPass, idShouldStop, isExtendedPass, isExtendedStop);
  }

  @Test
  public void checkMixedIdMaskPassFilter() {
    CanMessageFilter filter = new CanMessageFilter();
    filter.filterIdType = CanMessageFilter.FilterIdType.BOTH;
    filter.filterType = CanMessageFilter.FilterType.PASS;
    filter.filterMatchType = CanMessageFilter.FilterMatchType.MASK;
    filter.mask = 0x0F;
    filter.code = 0x03;
    int[] idShouldPass = {0x3, 0x13, 0x7F3, 0x3, 0x13, 0x7F3, 0x8F3, 0x1FFFFFF3};
    boolean[] isExtendedPass = {false, false, false, true, true, true, true, true};
    int[] idShouldStop = {0x2, 0x2, 0x1FFFFFFF};
    boolean[] isExtendedStop = {true, false, true};
    checkFilter(filter, idShouldPass, idShouldStop, isExtendedPass, isExtendedStop);
  }

  @Test
  public void checkStandardIdRangePassFilter() {
    CanMessageFilter filter = new CanMessageFilter();
    filter.filterIdType = CanMessageFilter.FilterIdType.STANDARD;
    filter.filterType = CanMessageFilter.FilterType.PASS;
    filter.filterMatchType = CanMessageFilter.FilterMatchType.RANGE;
    filter.idMin = 0x7F0;
    filter.idMax = 0x8FF;
    int[] idShouldPass = {0x7F0, 0x7FF};
    boolean[] isExtendedPass = {false, false};
    int[] idShouldStop = {0x7F0, 0x8F0, 0x900};
    boolean[] isExtendedStop = {true, true, true};
    checkFilter(filter, idShouldPass, idShouldStop, isExtendedPass, isExtendedStop);
  }

  @Test
  public void checkExtendedIdRangePassFilter() {
    CanMessageFilter filter = new CanMessageFilter();
    filter.filterIdType = CanMessageFilter.FilterIdType.EXTENDED;
    filter.filterType = CanMessageFilter.FilterType.PASS;
    filter.filterMatchType = CanMessageFilter.FilterMatchType.RANGE;
    filter.idMin = 0x7F0;
    filter.idMax = 0x8FF;
    int[] idShouldPass = {0x7F0, 0x7FF, 0x800, 0x8FF};
    boolean[] isExtendedPass = {true, true, true, true};
    int[] idShouldStop = {0x700, 0x900, 0x7F0};
    boolean[] isExtendedStop = {true, true, false};
    checkFilter(filter, idShouldPass, idShouldStop, isExtendedPass, isExtendedStop);
  }

  @Test
  public void checkMixedIdRangePassFilter() {
    CanMessageFilter filter = new CanMessageFilter();
    filter.filterIdType = CanMessageFilter.FilterIdType.BOTH;
    filter.filterType = CanMessageFilter.FilterType.PASS;
    filter.filterMatchType = CanMessageFilter.FilterMatchType.RANGE;
    filter.idMin = 0x7F0;
    filter.idMax = 0x8FF;
    int[] idShouldPass = {0x7F0, 0x7FF, 0x800, 0x8FF, 0x7F0};
    boolean[] isExtendedPass = {true, true, true, true, false};
    int[] idShouldStop = {0x700, 0x900, 0x700};
    boolean[] isExtendedStop = {true, true, false};
    checkFilter(filter, idShouldPass, idShouldStop, isExtendedPass, isExtendedStop);
  }

  @Test
  public void checkMultipleEqualFilters() {
    CanMessageFilter filter = new CanMessageFilter();
    filter.filterType = CanMessageFilter.FilterType.PASS;
    filter.filterMatchType = CanMessageFilter.FilterMatchType.RANGE;
    filter.idMin = 10;
    filter.idMax = 20;
    int[] idShouldPass = {10, 15, 20};
    int[] idShouldStop = {0, 9, 21};
    channel1.addFilter(filter);
    channel1.addFilter(filter);
    channel1.addFilter(filter);
    checkFilter(null, idShouldPass, idShouldStop, null, null);
  }

  @Test
  public void checkTwoDifferentPassFilters() {
    CanMessageFilter filterRange = new CanMessageFilter();
    filterRange.filterType = CanMessageFilter.FilterType.PASS;
    filterRange.filterMatchType = CanMessageFilter.FilterMatchType.RANGE;
    filterRange.idMin = 50;
    filterRange.idMax = 100;
    CanMessageFilter filterMask = new CanMessageFilter();
    filterMask.filterType = CanMessageFilter.FilterType.PASS;
    filterMask.filterMatchType = CanMessageFilter.FilterMatchType.MASK;
    filterMask.mask = 0x1F9;
    filterMask.code = 0x08;
    int[] idShouldPass = {50, 75, 100, 0x08, 0x0E, 0x0A};
    int[] idShouldStop = {49, 101};
    channel1.addFilter(filterRange);
    channel1.addFilter(filterMask);
    checkFilter(null, idShouldPass, idShouldStop, null, null);
  }

  @Test
  public void checkSimultaneousPassAndStopFilters() {
    CanMessageFilter filterPass = new CanMessageFilter();
    filterPass.filterType = CanMessageFilter.FilterType.PASS;
    filterPass.filterMatchType = CanMessageFilter.FilterMatchType.RANGE;
    filterPass.idMin = 50;
    filterPass.idMax = 100;
    CanMessageFilter filterStop = new CanMessageFilter();
    filterStop.filterType = CanMessageFilter.FilterType.STOP;
    filterStop.filterMatchType = CanMessageFilter.FilterMatchType.RANGE;
    filterStop.idMin = 80;
    filterStop.idMax = 120;
    int[] idShouldPass = {0, 49, 50, 79, 80, 100, 121};
    int[] idShouldStop = {101, 120};
    channel1.addFilter(filterStop);
    channel1.addFilter(filterPass);
    checkFilter(null, idShouldPass, idShouldStop, null, null);
  }

  @Test
  public void checkAddAndRemoveFilter() {
    CanMessageFilter filter = new CanMessageFilter();
    filter.filterType = CanMessageFilter.FilterType.PASS;
    filter.filterMatchType = CanMessageFilter.FilterMatchType.RANGE;
    filter.idMin = 10;
    filter.idMax = 20;
    int[] idShouldPass = {0, 9, 10, 15, 20, 21};
    int[] idShouldStop = {};
    channel1.addFilter(filter);
    channel1.removeFilter(filter);
    checkFilter(null, idShouldPass, idShouldStop, null, null);
  }

  @Test
  public void checkAddTwoAndRemoveOneFilter() {
    CanMessageFilter filter = new CanMessageFilter();
    filter.filterType = CanMessageFilter.FilterType.PASS;
    filter.filterMatchType = CanMessageFilter.FilterMatchType.RANGE;
    filter.idMin = 10;
    filter.idMax = 20;
    int[] idShouldPass = {0, 9, 10, 15, 20, 21};
    int[] idShouldStop = {};
    channel1.addFilter(filter);
    channel1.addFilter(filter);
    channel1.removeFilter(filter);
    checkFilter(null, idShouldPass, idShouldStop, null, null);
  }

  @Test
  public void checkClearFilters() {
    CanMessageFilter filter = new CanMessageFilter();
    filter.filterType = CanMessageFilter.FilterType.PASS;
    filter.filterMatchType = CanMessageFilter.FilterMatchType.RANGE;
    filter.idMin = 10;
    filter.idMax = 20;
    int[] idShouldPass = {0, 9, 10, 15, 20, 21};
    int[] idShouldStop = {};
    channel1.addFilter(filter);
    channel1.clearFilters();
    checkFilter(null, idShouldPass, idShouldStop, null, null);
  }

  /*
   * Applies filter to channel 1 and tests it by sending messages with IDs from testId from
   * channel 0 and expects them according to shouldPass.
   */
  private void checkFilter(CanMessageFilter filter, int[] idShouldPass, int[] idShouldStop,
                          boolean[] isExtendedPass, boolean[] isExtendedStop) {
    channel1.addFilter(filter);
    byte[] data;
    for (int i = 0; i < idShouldPass.length; i++) {
      data = getRandomData();
      CanMessage sendMessage = new CanMessage(idShouldPass[i], 8, data);
      CanMessage compareMessage = new CanMessage(idShouldPass[i], 8, data);
      if (isExtendedPass != null) {
        if (isExtendedPass[i]) {
          sendMessage.setFlag(CanMessage.MessageFlags.EXTENDED_ID);
          compareMessage.setFlag(CanMessage.MessageFlags.EXTENDED_ID);
        } else {
          sendMessage.setFlag(CanMessage.MessageFlags.STANDARD_ID);
          compareMessage.setFlag(CanMessage.MessageFlags.STANDARD_ID);
        }
      } else {
        sendMessage.setFlag(CanMessage.MessageFlags.STANDARD_ID);
        compareMessage.setFlag(CanMessage.MessageFlags.STANDARD_ID);
      }
      sendAndCompare(channel0, sendMessage, channel1, channel1, compareMessage,
                     CanMessage.Direction.RX);
      compareMessage.setFlag(CanMessage.MessageFlags.TX_ACK);
      sendAndCompare(channel0, sendMessage, channel1, channel0, compareMessage,
                     CanMessage.Direction.TX);
    }
    for (int i = 0; i < idShouldStop.length; i++) {
      data = getRandomData();
      CanMessage sendMessage = new CanMessage(idShouldStop[i], 8, data);
      CanMessage compareMessage = new CanMessage(idShouldStop[i], 8, data);
      if (isExtendedStop != null) {
        if (isExtendedStop[i]) {
          sendMessage.setFlag(CanMessage.MessageFlags.EXTENDED_ID);
          compareMessage.setFlag(CanMessage.MessageFlags.EXTENDED_ID);
        } else {
          sendMessage.setFlag(CanMessage.MessageFlags.STANDARD_ID);
          compareMessage.setFlag(CanMessage.MessageFlags.STANDARD_ID);
        }
      } else {
        sendMessage.setFlag(CanMessage.MessageFlags.STANDARD_ID);
        compareMessage.setFlag(CanMessage.MessageFlags.STANDARD_ID);
      }
      sendAndDoNotExpectMessage(channel0, sendMessage, channel1, channel1);
      compareMessage.setFlag(CanMessage.MessageFlags.TX_ACK);
      sendAndCompare(channel0, sendMessage, channel1, channel0, compareMessage,
                     CanMessage.Direction.TX);
    }
  }

  /*
   * Get a byte array of length 8 of random data
   */
  private byte[] getRandomData() {
    byte[] returnData = new byte[8];
    for (int i = 0; i < returnData.length; i++) {
      returnData[i] = (byte) (Math.random() * 0xFF);
    }
    return returnData;
  }

  /*
   * Send a message on sendChannel and expect no message event on targetChannel
   */
  private void sendAndDoNotExpectMessage(KvChannel sendChannel, CanMessage sendMessage,
                                         KvChannel targetChannel, KvChannel compareChannel) {
    MessageListener listener = new MessageListener();
    synchronized (waitLockMsg) {
      try {
        sendChannel.setBusParams(new CanBusParams(CanPredefinedBitRates.BITRATE_125K));
        targetChannel.setBusParams(new CanBusParams(CanPredefinedBitRates.BITRATE_125K));
        sendChannel.busOn();
        targetChannel.busOn();
        compareChannel.registerCanMessageListener(listener);
        messageWasReceived = false;
        sendChannel.write(sendMessage);
        waitLockMsg.wait(500);
        compareChannel.unregisterCanMessageListener(listener);
        assertThat(messageWasReceived, is(false));
      } catch (InterruptedException | CanLibException e) {
        e.printStackTrace();
        fail("Unexpected exception");
      }
    }
  }

  /*
   * Send a message from channel 0 to channel 1 and compared the received message
   */
  private void sendAndCompare(KvChannel sendChannel, CanMessage sendMessage,
                              KvChannel targetChannel, KvChannel compareChannel,
                              CanMessage compareMessage,
                              CanMessage.Direction expectedDirection) {
    MessageListener listener = new MessageListener();
    synchronized (waitLockMsg) {
      try {
        sendChannel.setBusParams(new CanBusParams(CanPredefinedBitRates.BITRATE_125K));
        targetChannel.setBusParams(new CanBusParams(CanPredefinedBitRates.BITRATE_125K));
        sendChannel.busOn();
        targetChannel.busOn();
        compareChannel.registerCanMessageListener(listener);
        messageWasReceived = false;
        sendChannel.write(sendMessage);
        waitLockMsg.wait(300);
        compareChannel.unregisterCanMessageListener(listener);
        assertThat(messageWasReceived, is(true));
        assertThat(receivedMessage.getFlags(), is(equalTo(compareMessage.getFlags())));
        assertThat(receivedMessage.getDirection(), is(equalTo(expectedDirection)));
        assertThat(receivedMessage.id, is(equalTo(compareMessage.id)));
        assertThat(receivedMessage.dlc, is(equalTo(compareMessage.dlc)));
        assertThat(receivedMessage.data, is(equalTo(compareMessage.data)));
      } catch (InterruptedException | CanLibException e) {
        e.printStackTrace();
        fail("Unexpected exception");
      }
    }
  }

  private class EventListener implements ChipStateListener {

    @Override
    public void chipStateEvent(ChipState chipState) {
      synchronized (waitLockState) {
        eventWasTriggered = true;
        KvChannelTest.chipState = chipState;
        waitLockState.notifyAll();
      }
    }
  }

  private class MessageListener implements CanMessageListener {

    @Override
    public void canMessageReceived(CanMessage message) {
      synchronized (waitLockMsg) {
        messageWasReceived = true;
        receivedMessage = message;
        waitLockMsg.notifyAll();
      }
    }
  }

}
