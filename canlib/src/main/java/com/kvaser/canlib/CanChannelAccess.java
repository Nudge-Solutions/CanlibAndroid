package com.kvaser.canlib;

import android.support.v4.util.*;

/**
 * Supplies static methods for obtaining and releasing access to CAN channels.
 */
class CanChannelAccess {

  /*
   * Maps string keys representing a channel on a unique device to an integer array i which the
   * first entry represents the current number KvChannels that has been open for a physical CAN
   * channel. The second array entry is larger than 0 if exclusive access to the channel has been
   * granted. The key string is formatted as serial number + EAN + channel index.
   */
  private static SimpleArrayMap<String, int[]> accessMap = new SimpleArrayMap<>();
  
  private CanChannelAccess() {
  }

  /*
   * Returns true if non-exclusive access was obtained.
   */
  synchronized public static boolean getAccess(KvDevice device, int channelIndex) {
    int[] entry = accessMap
        .get("" + device.getSerialNumber() + device.getEan().getEanString() + channelIndex);
    if (entry != null) {
      if (entry[1] != 0) {
        return false;
      } else {
        accessMap
            .put("" + device.getSerialNumber() + device.getEan().getEanString() + channelIndex,
                 new int[] {entry[0] + 1, entry[1]});
      }
    } else {
      accessMap.put("" + device.getSerialNumber() + device.getEan().getEanString() + channelIndex,
                    new int[] {1, 0});
    }
    return true;
  }

  /*
   * Returns true if exclusive access was obtained.
   */
  synchronized public static boolean getExclusiveAccess(KvDevice device, int channelIndex) {
    if (accessMap.get("" + device.getSerialNumber() + device.getEan().getEanString() + channelIndex)
        != null) {
      return false;
    } else {
      accessMap.put("" + device.getSerialNumber() + device.getEan().getEanString() + channelIndex,
                    new int[] {1, 1});
      return true;
    }
  }

  /*
   * Releases the access to a channel (reduces the number of accesses to a channel by 1).
   */
  synchronized public static void releaseAccess(KvDevice device, int channelIndex) {
    int[] entry = accessMap
        .get("" + device.getSerialNumber() + device.getEan().getEanString() + channelIndex);
    if (entry != null) {
      if (entry[1] != 0 || entry[0] <= 1) {
        accessMap
            .remove("" + device.getSerialNumber() + device.getEan().getEanString() + channelIndex);
      } else {
        accessMap
            .put("" + device.getSerialNumber() + device.getEan().getEanString() + channelIndex,
                 new int[] {entry[0] - 1, entry[1]});
      }
    }
  }

  /*
   * Releases all channel accesses to a device.
   */
  synchronized public static void releaseAccessPerDevice(String serialNumber, String ean,
                                                         int numberOfChannels) {
    for (int i = 0; i < numberOfChannels; i++) {
      accessMap.remove("" + serialNumber + ean + i);
    }
  }

  /*
   * Resets the access map, releasing all channel accesses.
   */
  synchronized public static void resetAccess() {
    accessMap = new SimpleArrayMap<>();
  }
}
