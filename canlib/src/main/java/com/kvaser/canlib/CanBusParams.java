package com.kvaser.canlib;

import android.support.annotation.*;
/**
 * The CanBusParams class is used for representing CAN bus parameters passed in KvChannel
 * operations.
 */
public class CanBusParams {

  /** Bit rate (measured in bits per second) */
  @IntRange(from = 1)
  public long bitRate;
  /**
   * Time segment 1 (number of quanta from, but not including, the Sync Segment to the sampling
   * point).
   */
  @IntRange(from = 1, to = 16)
  public int tseg1;
  /** Time segment 2 (number of quanta from the sampling point to the end of the bit). */
  @IntRange(from = 1, to = 8)
  public int tseg2;
  /** The Synchronization Jump Width; can be 1, 2, 3 or 4. */
  @IntRange(from = 1, to = 4)
  public int sjw;
  /** The number of sampling points; can only be 1. */
  public static final int numSamplingPoints = 1;

  /**
   * Instantiating the class with no arguments sets a default bit rate of BITRATE_125K.
   */
  public CanBusParams() {
    SetDefaultParameters(CanPredefinedBitRates.BITRATE_125K);
  }

  /**
   * The bit rate can be set to one of the predefined values using a constructor argument. This
   * will set all timing parameters to predefined values for the selected bit rate (the number of
   * sampling points used are set to 1).
   *
   * @param bitRate Enum object describing the bit rate to set.
   * @see CanPredefinedBitRates DefaultBitRates
   */
  public CanBusParams(CanPredefinedBitRates bitRate) {
    SetDefaultParameters(bitRate);
  }

  /**
   * All bus parameters can be set using this constructor (the number of sampling points used are
   * set to 1).
   *
   * @param bitRate Bit rate.
   * @param tseg1   Time segment 1.
   * @param tseg2   Time segment 2.
   * @param sjw     Synchronization Jump Width.
   */
  public CanBusParams(long bitRate, int tseg1, int tseg2, int sjw) {
    this.bitRate = bitRate;
    this.tseg1 = tseg1;
    this.tseg2 = tseg2;
    this.sjw = sjw;
  }

  private void SetDefaultParameters(CanPredefinedBitRates bitRate) {
    switch (bitRate) {
      case BITRATE_1M:
        this.bitRate = 1000000L;
        this.tseg1 = 5;
        this.tseg2 = 2;
        this.sjw = 1;
        break;

      case BITRATE_500K:
        this.bitRate = 500000L;
        this.tseg1 = 5;
        this.tseg2 = 2;
        this.sjw = 1;
        break;

      case BITRATE_250K:
        this.bitRate = 250000L;
        this.tseg1 = 5;
        this.tseg2 = 2;
        this.sjw = 1;
        break;

      case BITRATE_125K:
        this.bitRate = 125000L;
        this.tseg1 = 11;
        this.tseg2 = 4;
        this.sjw = 1;
        break;

      case BITRATE_100K:
        this.bitRate = 100000L;
        this.tseg1 = 11;
        this.tseg2 = 4;
        this.sjw = 1;
        break;

      case BITRATE_83K:
        this.bitRate = 83333L;
        this.tseg1 = 5;
        this.tseg2 = 2;
        this.sjw = 2;
        break;

      case BITRATE_62K:
        this.bitRate = 62500L;
        this.tseg1 = 11;
        this.tseg2 = 4;
        this.sjw = 1;
        break;

      case BITRATE_50K:
        this.bitRate = 50000L;
        this.tseg1 = 11;
        this.tseg2 = 4;
        this.sjw = 1;
        break;

      case BITRATE_10K:
      default:
        this.bitRate = 10000L;
        this.tseg1 = 11;
        this.tseg2 = 4;
        this.sjw = 1;
        break;

    }
  }
}
