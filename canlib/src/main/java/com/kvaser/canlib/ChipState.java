package com.kvaser.canlib;

import java.util.*;
/**
 * Defines the status of the chip for a specific channel at a specific point in time.
 */
public class ChipState {
  
  /** Timestamp of chip status in 10us resolution */
  long time;
  
  /** Current Tx error counter in CAN controller */
  int txErrorCounter;
  
  /** Current Rx error counter in CAN controller */
  int rxErrorCounter;
  
  /** A set of the current bus status(es) */
  EnumSet<BusStatus> busStatus;
  
  /** The channel index that this chip status applies to */
  byte channel;
  
  /**
   * Returns the timestamp of the chip state.
   *
   * @return the timestamp of this chip state in 10us resolution
   */
  public long getTimeStamp() {
    return time;
  }
  
  /**
   * Returns the Tx error counter of the chip state.
   *
   * @return the current Tx error counter in CAN controller
   */
  public int getTxErrorCounter() {
    return txErrorCounter;
  }
  
  /**
   * Returns the Rx error counter of the chip state.
   *
   * @return the Rx error counter in CAN controller
   */
  public int getRxErrorCounter() {
    return rxErrorCounter;
  }
  
  /**
   * Returns the channel index that this chip state applies to.
   *
   * @return the channel index that this chip state applies to
   */
  public byte getChannel() {
    return channel;
  }
  
  /**
   * Returns an enum set of bus status flags.
   *
   * @return a set of the current bus status(es)
   */
  public EnumSet<BusStatus> getBusStatus() {
    return busStatus;
  }
  
  /**
   * Checks if a specific bus status is active in this chip status.
   *
   * @param status The bus status to look for
   * @return true if the requests status is active, false otherwise
   */
  public boolean isBusStatusSet(BusStatus status) {
    return busStatus.contains(status);
  }
  
  /** Status of the CAN bus */
  public enum BusStatus {
    ERROR_ACTIVE, ERROR_WARNING, ERROR_PASSIVE, BUSOFF
  }
}
