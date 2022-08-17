package com.kvaser.canlib;

/**
 * This class defines CAN message filters based on the ID of the message. The default filter on a
 * new object is a {@link com.kvaser.canlib.CanMessageFilter.FilterMatchType#RANGE range} filter
 * that passes all standard IDs.
 */
public class CanMessageFilter {

  /**
   * Defines which filter parameters that shall be applied, see {@link
   * com.kvaser.canlib.CanMessageFilter.FilterMatchType}.
   */
  public FilterMatchType filterMatchType = FilterMatchType.RANGE;
  /**
   * Defines if this is a pass or stop filter, see {@link com.kvaser.canlib.CanMessageFilter.FilterType}.
   */
  public FilterType filterType = FilterType.PASS;
  /**
   * Defines if the filter filters on standard or extended IDs, see {@link
   * com.kvaser.canlib.CanMessageFilter.FilterIdType}.
   */
  public FilterIdType filterIdType = FilterIdType.STANDARD;
  /**
   * Defines the lowest (inclusive) ID that will match the filter when range filtering is applied,
   * see {@link com.kvaser.canlib.CanMessageFilter.FilterMatchType#RANGE}.
   */
  public int idMin = 0;
  /**
   * Defines the highest (inclusive) ID that will match the filter when range filtering is applied,
   * see {@link com.kvaser.canlib.CanMessageFilter.FilterMatchType#RANGE}.
   */
  public int idMax = 0xffffffff;
  /**
   * Defines the mask used when mask filtering is applied, see {@link
   * com.kvaser.canlib.CanMessageFilter.FilterMatchType#MASK}.
   */
  public int mask = 0;
  /**
   * Defines the code to match when mask filtering is applied, see {@link
   * com.kvaser.canlib.CanMessageFilter.FilterMatchType#MASK}.
   */
  public int code = 0;

  /**
   * Creates a string representing the ID bitmask for standard IDs based on the {@link #mask} and
   * {@link #code} that is currently set in the filter.
   *
   * @return bitmask string.
   */
  public String getBitMaskStandard() {
    String bitMask = "";
    for (int i = 0; i < 11; i++) {
      if (((mask >> i) & 0x1) == 0) {
        bitMask = "X" + bitMask;
      } else if (((code >> i) & 0x1) == 0) {
        bitMask = "0" + bitMask;
      } else {
        bitMask = "1" + bitMask;
      }
    }
    return bitMask;
  }

  /**
   * Creates a string representing the ID bitmask for extended IDs based on the {@link #mask} and
   * {@link #code} that is currently set in the filter.
   *
   * @return bitmask string.
   */
  public String getBitMaskExtended() {
    String bitMask = "";
    for (int i = 0; i < 29; i++) {
      if (((mask >> i) & 0x1) == 0) {
        bitMask = "X" + bitMask;
      } else if (((code >> i) & 0x1) == 0) {
        bitMask = "0" + bitMask;
      } else {
        bitMask = "1" + bitMask;
      }
    }
    return bitMask;
  }

  /**
   * Runs the filter on a CanMessage object
   *
   * @param msg The CAN message to filter
   * @return true if the message passes the filter, false if it does not pass the filter
   */
  boolean filter(CanMessage msg) {

    final long STANDARD_ID_MASK = 0x7FF;
    final long EXTENDED_ID_MASK = 0x1FFFFFFF;
    long mainMask;
    if (msg.isFlagSet(CanMessage.MessageFlags.EXTENDED_ID)) {
      mainMask = EXTENDED_ID_MASK;
    } else {
      mainMask = STANDARD_ID_MASK;
    }
    boolean messageMatchesFilter = false;
    if ((mainMask == EXTENDED_ID_MASK && filterIdType == FilterIdType.EXTENDED) || (
        mainMask == STANDARD_ID_MASK && filterIdType == FilterIdType.STANDARD) ||
        filterIdType == FilterIdType.BOTH) {
      switch (filterMatchType) {
        case MASK:
          messageMatchesFilter = filterByMask(msg.id, mainMask);
          break;
        case RANGE:
          messageMatchesFilter = filterByRange(msg.id, mainMask);
          break;
        case MASK_AND_RANGE:
          messageMatchesFilter = filterByMask(msg.id, mainMask) && filterByRange(msg.id, mainMask);
          break;
        case MASK_OR_RANGE:
          messageMatchesFilter = filterByMask(msg.id, mainMask) || filterByRange(msg.id, mainMask);
          break;
        default:
          break;
      }
    }

    if (filterType == FilterType.PASS) {
      return messageMatchesFilter;
    } else {
      return !messageMatchesFilter;
    }

  }

  private boolean filterByMask(long id, long idMask) {
    return (((id & idMask) & mask) == ((code & idMask) & mask));
  }

  private boolean filterByRange(long id, long idMask) {
    return (((id & idMask) >= idMin) && ((id & idMask) <= idMax));
  }

  /** Describes which filter parameters that shall be applied in filter matching. */
  public enum FilterMatchType {
    /**
     * Use {@link #mask mask} and {@link #code code} as filter on the message ID.<br>
     * Filter matches when all bits in {@link #code code}, that are active (set to 1) in the {@link
     * #mask mask}, matches the corresponding bit in the message ID.<br>
     * Note that for standard IDs only the least significant 11 bits of {@link #mask mask} and
     * {@link #code code} will be considered. Same principle is applied on extended IDs, but then
     * the least significant 29 bits are considered.
     */
    MASK,
    /**
     * The filter will match the message ID if it is in the range {@link #idMin idMin} &lt;= ID
     * &lt;= {@link #idMax idMax}
     */
    RANGE,
    /**
     * The filter matches when both filters as described in {@link #MASK} and {@link #RANGE}
     * matches.
     */
    MASK_AND_RANGE,
    /**
     * The filter matches when at least one of the filters as described in {@link #MASK} and {@link
     * #RANGE} matches.
     */
    MASK_OR_RANGE
  }

  /** Defines the filter as a pass filter or a stop filter. */
  public enum FilterType {
    /** All messages that matches the filter will pass. All other messages will not pass. */
    PASS,
    /** All messages that matches the filter will not pass. All other messages will pass. */
    STOP
  }

  /** Defines which ID type is filtered. */
  public enum FilterIdType {
    /** Filter standard IDs */
    STANDARD,
    /** Filter extended IDs */
    EXTENDED,
    /** Filter both standard and extended IDs */
    BOTH,
  }
}
