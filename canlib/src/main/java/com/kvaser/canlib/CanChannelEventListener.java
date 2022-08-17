package com.kvaser.canlib;

/**
 * The intention is that a KvChannel shall implement this interface to be able receive events on a
 * CAN channel from the driver.
 * The events could be CAN Rx, CAN Tx ack, CAN errors, and TBD.
 */
interface CanChannelEventListener {
  
  int getChannelIndex();
  
  /**
   * Called by driver upon channel event.
   *
   * @param eventType The type of event
   * @param eventData If eventType = MESSAGE then this is a CanMessage object
   *                  If eventType = ERROR then this is a CanError object
   */
  void canChannelEvent(CanChannelEventType eventType, Object eventData);
  
  /**
   * Event type
   */
  enum CanChannelEventType {
    MESSAGE, CHIP_STATE
  }
}
