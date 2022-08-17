package com.kvaser.canlib;

/**
 * Implement this interface to get a listener to register for receiving changes in chip state (i.e.
 * error active, passive, warning and busoff plus error counters) from CanLib KvChannels.
 */
public interface ChipStateListener {

  /**
   * Called by CanLib when chip state information has been received from the device.
   *
   * @param chipState The new chip state
   */
  void chipStateEvent(ChipState chipState);
}
