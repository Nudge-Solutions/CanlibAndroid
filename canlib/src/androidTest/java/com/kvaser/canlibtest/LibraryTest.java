package com.kvaser.canlibtest;

import org.junit.runner.*;
import org.junit.runners.*;

/**
 * Test suit class which runs all of CanLib's API tests.
 *
 * This test assumes that one Kvaser Memorator Pro is connected to the target device and that the
 * USB permission prompt is accepted if shown when the test is run. Furthermore, it is assumed that
 * channel 0 and 1 are connected.
 *
 * All of the Memorator's LEDs should flash during the test.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({CanLibTest.class,
                     KvDeviceTest.class,
                     KvChannelTest.class,
                     KvDeviceVirtualTest.class,
                     KvChannelVirtualTest.class})
public class LibraryTest {

}
