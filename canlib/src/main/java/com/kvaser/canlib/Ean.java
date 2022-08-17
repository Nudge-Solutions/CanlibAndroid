package com.kvaser.canlib;

import java.nio.*;
import java.util.Arrays;

/**
 * This class handles EAN numbers and converts it between different formats.
 */
public class Ean {

  private byte[] ean = new byte[8];

  /**
   * Creates an Ean object initialized with all zeroes.
   */
  public Ean() {
    Arrays.fill(ean, (byte) 0);
  }

  /**
   * Creates an Ean object based on the values in a BCD coded byte array in the same way as
   * {@link #setEan(byte[])}
   *
   * @param ean The EAN in a BCD coded array of 8 bytes
   */
  public Ean(byte[] ean) {
    this.ean = Arrays.copyOf(ean, this.ean.length);
  }

  /**
   * Creates an Ean object based on a String in the same way as {@link #setEan(String)}
   *
   * Example:
   * "ean:007 330 130 007 789" corresponds to "73-30130-00778-9"
   *
   * @param ean The ean as a String of digits and optional delimiters.
   */
  public Ean(String ean) {
    this.ean = eanStringToBcd(ean);
  }

  /**
   * Creates an Ean object based on the values in a BCD coded integer array in the same way as
   * {@link #setEan(int[])}
   *
   * @param ean The EAN in a BCD coded array of 2 ints
   */
  public Ean(int[] ean) {
    this.ean = eanIntToBcd(ean);
  }

  /**
   * Compares the values of two Ean objects.
   *
   * @param ean1 One Ean object to compare.
   * @param ean2 The other Ean object to compare.
   * @return true if the values in the two Ean are equal, false otherwise
   */
  public static boolean equals(Ean ean1, Ean ean2) {
    return Arrays.equals(ean1.getEanByte(), ean2.getEanByte());
  }

  /**
   * Sets the EAN value based on the values in a BCD coded byte array.
   * Only the first 8 bytes of the array will be used,
   * if the array is longer the following bytes will be ignored,
   * if the array is shorter it will be padded with zeroes.
   *
   * Array index 0 is the least significant byte and array index 7 is the most significant byte.
   *
   * Example:
   * {0x89, 0x77, 0x00, 0x30, 0x01, 0x33, 0x07, 0x00} corresponds to "73-30130-00778-9"
   *
   * @param ean The EAN in a BCD coded array of 8 bytes
   */
  public void setEan(byte[] ean) {
    this.ean = Arrays.copyOf(ean, this.ean.length);
  }

  /**
   * Sets the EAN value based on the values in a BCD coded integer array.
   * Only the first 2 integers of the array will be used,
   * if the array is longer the following integers will be ignored
   * if the array is shorter it will be padded with zeroes.
   *
   * Array index 0 is the least significant int and array index 1 is the most significant int.
   *
   * Example:
   * {0x30007789, 0x00073301} corresponds to "73-30130-00778-9"
   *
   * @param ean The EAN in a BCD coded array of 2 ints
   */
  public void setEan(int[] ean) {
    this.ean = eanIntToBcd(ean);
  }

  /**
   * Sets the EAN value based on a String. The String may or may not include optional dashes or
   * other delimiters, only digits will be taken into consideration. In case the String contains
   * more than 16 digits then it will be truncated.
   *
   * Example:
   * "ean:007 330 130 007 789" corresponds to "73-30130-00778-9"
   *
   * @param ean The ean as a String of digits and optional delimiters.
   */
  public void setEan(String ean) {
    this.ean = eanStringToBcd(ean);
  }

  /**
   * Gets the EAN as String on the format ddddd-ddddd-ddddd-d whe each d represents a digit.
   * Leading
   * zeroes will be stripped.
   *
   * @return EAN as String
   */
  public String getEanString() {
    return bcdToEanString(ean);
  }

  /**
   * Gets the EAN as a bcd coded int array of length 2. First int is least significant.
   *
   * @return EAN as BCD coded int array
   */
  public int[] getEanInt() {
    return bcdToEanInt(ean);
  }

  /**
   * Gets the EAN as a bcd coded byte array of length 8. First byte is least significant.
   *
   * @return EAN as BCD coded byte array
   */
  private byte[] getEanByte() {
    return ean;
  }

  /**
   * Gets the product number as string. This corresponds to the last 7 characters (last 6 digits)
   * of the EAN String representation.
   *
   * @return EAN as BCD coded byte array
   */
  public String getProductNumber() {
    String eanString = bcdToEanString(ean);
    if (eanString.length() > 7) {
      return eanString.substring(eanString.length() - 7);
    } else {
      return eanString;
    }
  }

  /**
   * Compares the values of this Ean with another Ean.
   *
   * @param compareToEan The Ean object to compare this to.
   * @return true if the values in the two Ean are equal, false otherwise
   */
  public boolean equals(Ean compareToEan) {
    return Arrays.equals(ean, compareToEan.getEanByte());
  }

  private String bcdToEanString(byte[] bcd) {
    return String.format("%4x%01x-%01x%02x%02x-%02x%02x%01x-%01x",
                         (bcd[7] << 4) | bcd[6], (bcd[5] >>> 4) & 0xf, (bcd[5] & 0xf), bcd[4],
                         bcd[3], bcd[2], bcd[1], (bcd[0] >>> 4) & 0xf, (bcd[0] & 0xf));
  }

  private int[] bcdToEanInt(byte[] bcd) {
    return new int[] {((bcd[3] << 24) | (bcd[2] << 16) | (bcd[1] << 8) | (((int) bcd[0]) & 0xff)),
                      ((bcd[7] << 24) | (bcd[6] << 16) | (bcd[5] << 8) | (((int) bcd[4]) & 0xff))};
  }

  private byte[] eanStringToBcd(String text) {
    int numBytes = 8;
    byte[] bcd = new byte[numBytes];
    int numChars = numBytes * 2;

    String textDigits = text.replaceAll("[^0-9]", "");
    int textLen = textDigits.length();
    if (textLen > numChars) {
      textDigits = textDigits.substring(0, numChars);
    }
    Arrays.fill(bcd, (byte) 0);
    int offset = numChars - textLen;
    for (int i = offset; i < numChars; i++) {
      String thisDigit = textDigits.substring(i - offset, i - offset + 1);
      bcd[i / 2] |= (byte) (Integer.parseInt(thisDigit) << ((i % 2 == 0) ? 4 : 0));
    }
    return bcd;
  }

  private byte[] eanIntToBcd(int[] values) {
    int numBytes = 8;
    byte[] bcd = new byte[numBytes];
    Arrays.fill(bcd, (byte) 0);
    ByteBuffer bcdBuffer = ByteBuffer.wrap(bcd);
    bcdBuffer.order(ByteOrder.BIG_ENDIAN);
    bcdBuffer.rewind();
    if (values.length > 1) {
      bcdBuffer.putInt(values[1]);
    } else {
      bcdBuffer.putInt(0);
    }
    if (values.length > 0) {
      bcdBuffer.putInt(values[0]);
    }
    return bcd;
  }
}
