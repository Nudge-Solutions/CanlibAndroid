package com.kvaser.canlib;

import android.support.annotation.*;

import java.util.*;

/**
 * The CanMessage class is used for representing messages passed in KvChannel operations and
 * CanMessageListener callbacks.
 */
public class CanMessage {

  /** ID of CAN message */
  public int id;

  /** DLC of CAN message */
  public int dlc;

  /** CAN message data bytes */
  public byte[] data = new byte[8];

  /** Timestamp of CAN message in 10us resolution (only applies to received messages) */
  long time;

  /** Direction of CAN message */
  Direction direction;

  /** Flags */
  final EnumSet<MessageFlags> flags;

  /**
   * Creates a new CanMessage with all values copied from message.
   */
  public CanMessage(CanMessage message) {
    this.id = message.id;
    this.dlc = message.dlc;
    this.data = Arrays.copyOf(message.data, 8);
    this.flags = EnumSet.copyOf(message.flags);
    this.direction = message.direction;
    this.time = message.time;
  }

  /**
   * Creates a new CanMessage with default values that should then be set separately.
   * The default values is a standard ID of 0x7ff, DLC of 8 and all data bytes set to zero.
   */
  public CanMessage() {
    this.id = 0x7FF;
    this.dlc = 8;
    Arrays.fill(this.data, (byte) 0);
    this.flags = EnumSet.noneOf(MessageFlags.class);
    this.direction = CanMessage.Direction.TX;
    this.time = -1;
  }

  /**
   * Creates a new CanMessage with the ID, DLC and data supplied as arguments.
   *
   * @param id   Message ID, 11 or 29 bits.
   *             Validity of ID is not checked here since this constructor does not take flags as
   *             an
   *             argument, which means it is not known if this is supposed to be a standard or an
   *             extended ID. The valid range for ID is checked when the message object is passed
   *             to
   *             the {@link KvChannel#write(CanMessage)} method.
   * @param dlc  Message DLC, valid range: 0-8 (or 0-15).
   *             The valid range is depending on the KvChannel where the message object will be
   *             used, i.e. if it has been opened with the flag {@link com.kvaser.canlib.KvChannel.ChannelFlags#ACCEPT_LARGE_DLC
   *             ChannelFlags.ACCEPT_LARGE_DLC} or not. The valid range for DLC is checked when the
   *             message object is passed to the {@link KvChannel#write(CanMessage)} method.
   * @param data Message data, an array of maximum 8 bytes length.
   *             If the data array is longer than 8 bytes then only the first 8 bytes will be
   *             stored
   *             in this object. If it is shorter than 8 bytes the remaining bytes in the object
   *             will be set to zero.
   */
  public CanMessage(int id, int dlc, @NonNull byte[] data) {
    this.id = id;
    this.dlc = dlc;
    for (int i = 0; (i < data.length) && (i < 8); i++) {
      this.data[i] = data[i];
    }

    this.flags = EnumSet.noneOf(MessageFlags.class);
    this.direction = CanMessage.Direction.TX;
    this.time = -1;
  }

  /**
   * Returning the timestamp of a CAN message.
   * Note that the timestamp will only be valid in the case when the message has been received
   * through the CanMessageListener interface, i.e. when the CanMessage has been received on CAN Rx
   * or has been acknowledged on CAN Tx.
   *
   * @return The timestamp in 10us resolution. -1 is returned in the case when a CanMessage object
   * does not originate from CAN Rx or CAN Tx ack.
   */
  public long getTimestamp() {
    return time;
  }

  /**
   * Returning the direction of a CAN message.
   * Note that the direction will only be valid in the case when the message has been received
   * through the CanMessageListener interface, i.e. when the CanMessage has been received on CAN Rx
   * or has been acknowledged on CAN Tx.
   *
   * @return The direction. Tx is returned in the case when a CanMessage object does not originate
   * from CAN Rx or CAN Tx ack (it is assumed that the creator of the object is intending to send
   * the CanMessage).
   */
  public Direction getDirection() {
    return direction;
  }

  /**
   * Returning the flags of a CAN message.
   * Note:0 Most flags are set by CanLib upon receiving a CAN Rx or a CAN Tx, this means that the
   * flags are properly set when a message is received through the CanMessageListener interface.
   *
   * @return The flags in an EnumSet.
   */
  public EnumSet<MessageFlags> getFlags() {
    return EnumSet.copyOf(flags);
  }

  /**
   * Adds one flag to the message.
   *
   * @param flag The flag to add.
   */
  public void setFlag(MessageFlags flag) {
    synchronized (flags) {
      flags.add(flag);
    }
  }

  /**
   * Adds one flag to the message.
   *
   * @param flag The flag to remove.
   */
  public void removeFlag(MessageFlags flag) {
    synchronized (flags) {
      flags.remove(flag);
    }
  }

  /**
   * Checks if a specified flag is set.
   *
   * @param flag The flag to look for.
   * @return true if the flag is set, false if it is not set
   */
  public boolean isFlagSet(MessageFlags flag) {
    synchronized (flags) {
      return flags.contains(flag);
    }
  }

  short getFlagsAsMask() {
    short bitField = 0;
    synchronized (flags) {
      for (MessageFlags flag : flags) {
        bitField |= flag.getMask();
      }
    }
    return bitField;
  }

  void setFlagsUsingBitField(short flagsBitField) {
    synchronized (flags) {
      flags.clear();
      for (MessageFlags flag : MessageFlags.values()) {
        if ((flagsBitField & flag.getMask()) != 0) {
          flags.add(flag);
        }
      }
    }
  }

  void setFlagsUsingBitField(byte flagsBitField) {
    short flagsBitfieldShrt = (short) ((int) flagsBitField & 0xff);
    synchronized (flags) {
      for (MessageFlags flag : flags) {
        if (((int) flag.getMask() & 0xffff) < 0x100) {
          flags.remove(flag);
        }
      }
      for (MessageFlags flag : MessageFlags.values()) {
        if ((flagsBitfieldShrt & flag.getMask()) != 0) {
          flags.add(flag);
        }
      }
    }
  }

  /**
   * Describes the direction of the CAN message.
   */
  public enum Direction {
    /** CAN Rx */
    RX,
    /** CAN Tx */
    TX
  }

  /**
   * Enum containing flags used in CanMessages.
   */
  public enum MessageFlags {
    /** Message has a standard ID */
    STANDARD_ID((short) 0x0000),  // Mask is zero because it is not part of the flags on USB
    /** Message has an extended ID */
    EXTENDED_ID((short) 0x0000), // Mask is zero because it is not part of the flags on USB
    /** Message is an error frame */
    ERROR_FRAME((short) 0x0001),
    /** HW buffer overrun */
    ERR_HW_OVERRUN((short) 0x0002),
    /** SW buffer overrun */
    ERR_SW_OVERRUN((short) 0x0002),
    /** Message is a remote request */
    REMOTE_REQUEST((short) 0x0010),
    /** Message is a TX ACK (msg is really sent) */
    TX_ACK((short) 0x0040),
    /** Message is a TX REQUEST (msg is transferred to the chip) */
    TX_RQ((short) 0x0080);
    //FD_ESI((short)0x0400),
    //FD_BRS((short)0x0200),
    //FD_EDL((short)0x0100);

    private final short mask;

    MessageFlags(short mask) {
      this.mask = mask;
    }

    private short getMask() {
      return mask;
    }
  }
}
