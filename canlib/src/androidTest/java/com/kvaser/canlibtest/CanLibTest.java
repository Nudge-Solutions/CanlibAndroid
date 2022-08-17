package com.kvaser.canlibtest;

import org.junit.*;
import android.support.test.*;
import android.content.*;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

import com.kvaser.canlib.*;

/**
 * Test class for the top-level library class: CanLib
 *
 * This test assumes that one Kvaser Memorator Pro is connected to the target device and that the
 * USB permission prompt is accepted if shown when the test is run.
 */
public class CanLibTest {

  private static CanLib canLib;
  private static Context context;

  @BeforeClass
  public static void testSetup() {
    context = InstrumentationRegistry.getContext();
    canLib = CanLib.getInstance(context);
  }

  @Test
  public void testInstanceCreation() {
    assertThat(canLib, is(notNullValue()));
    CanLib secondInstance = CanLib.getInstance(context);
    assertThat(secondInstance, is(equalTo(canLib)));
  }

  @Test
  public void testGetNumberOfDevicesWithVirtual() {
    assertThat(canLib.getNumberOfDevices(), is(equalTo(2)));
  }

  @Test
  public void testGetNumberOfDevicesWithoutVirtual() {
    canLib.setVirtualDeviceState(false);
    assertThat(canLib.getNumberOfDevices(), is(equalTo(1)));
    canLib.setVirtualDeviceState(true);
  }

  @Test
  public void testGetDeviceByIndex() {
    assertThat(canLib.getDevice(0), is(notNullValue()));
    assertThat(canLib.getDevice(1), is(notNullValue()));
    assertThat(canLib.getDevice(2), is(nullValue()));
    assertThat(canLib.getDevice(-1), is(nullValue()));
    assertThat(canLib.getDevice(1000), is(nullValue()));
  }

  @Test
  public void testGetDeviceByEan() {
    KvDevice device = canLib.getDevice(1);
    Ean ean = device.getEan();
    assertThat(canLib.getDevice(ean).getEan(), is(equalTo(device.getEan())));
    assertThat(canLib.getDevice(new Ean()), is(nullValue()));
  }

  @Test
  public void testGetDeviceByEanAndSerial() {
    KvDevice device = canLib.getDevice(1);
    Ean ean = device.getEan();
    int serial = device.getSerialNumber();
    assertThat(canLib.getDevice(ean, serial).getEan(), is(equalTo(device.getEan())));
    assertThat(canLib.getDevice(ean, serial + 1), is(nullValue()));
    assertThat(canLib.getDevice(new Ean(), serial), is(nullValue()));
  }
}