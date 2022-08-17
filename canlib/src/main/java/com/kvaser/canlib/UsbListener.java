package com.kvaser.canlib;


/**
 * This interface describes the methods that should be implemented by a class registering a
 * listener to be called on USB events.
 */
interface UsbListener {

  public void UsbDataReceived(byte[] bytes);
}

