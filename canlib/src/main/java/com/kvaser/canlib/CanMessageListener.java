package com.kvaser.canlib;

/**
 * Implement this interface to get a listener to register for receiving CAN messages from CanLib KvChannels.
 * The received messages may be messages received on CAN (CAN Rx) as well as transmitted messages
 * (CAN Tx ack).
 */
public interface CanMessageListener {

  /**
   * Called by CanLib when a CAN Rx message is received or a CAN Tx message is acknowledged.
   *
   * @param msg The received CAN message
   */
  void canMessageReceived(CanMessage msg);
}
