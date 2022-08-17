package com.kvaser.canlib;

/**
 * This exception is thrown by CanLib's APIs when an error has occurred which prevents the API to
 * execute fully. Get methods provide an interface for getting two levels of information regarding
 * the exception: the error code (the group to which the error belongs) and the error detail (more
 * detailed information regarding the error).
 */
public class CanLibException extends Exception {

  private final ErrorCode errorCode;
  private final ErrorDetail errorDetail;

  CanLibException(ErrorCode errorCode) {
    super(errorCode.toString());
    this.errorCode = errorCode;
    this.errorDetail = null;
  }

  CanLibException(ErrorCode errorCode, ErrorDetail errorDetail) {
    super(errorCode.toString() + " " + errorDetail.toString());
    this.errorCode = errorCode;
    this.errorDetail = errorDetail;
  }

  CanLibException(ErrorCode errorCode, ErrorDetail errorDetail, long value) {
    super(errorCode.toString() + " " + errorDetail.toString() + "(" + value + ")");
    this.errorCode = errorCode;
    this.errorDetail = errorDetail;
  }

  CanLibException(ErrorCode errorCode, ErrorDetail errorDetail, long value, String msg) {
    super(errorCode.toString() + " " + errorDetail.toString() + "(" + value + "): " + msg);
    this.errorCode = errorCode;
    this.errorDetail = errorDetail;
  }

  CanLibException(ErrorCode errorCode, String msg) {
    super(errorCode.toString() + ": " + msg);
    this.errorCode = errorCode;
    this.errorDetail = null;
  }

  CanLibException(ErrorCode errorCode, ErrorDetail errorDetail, String msg) {
    super(errorCode.toString() + " " + errorDetail.toString() + ": " + msg);
    this.errorCode = errorCode;
    this.errorDetail = errorDetail;
  }

  /**
   * Returns the error code of the exception.
   *
   * @return Returns the error code of the exception.
   */
  public ErrorCode getErrorCode() {
    return errorCode;
  }

  /**
   * Returns the error detail of the exception.
   *
   * @return Returns the error detail of the exception.
   */
  public ErrorDetail getErrorDetail() {
    return errorDetail;
  }

  /**
   * Enum containing the error codes that constitute the top level error information of the
   * CanLibException.
   */
  public enum ErrorCode {
//    canERR__RESERVED,
//    canERR_SCRIPT_WRONG_VERSION,
//    canERR_SCRIPT_FAIL,
//    canERR_MEMO_FAIL,
//    canERR_CONFIG,
//    canERR_CRC,
//    canERR_DISK,
//    canERR_HOST_FILE,
//    canERR_DEVICE_FILE,
//    /** Not implemented */
//    canERR_NOT_IMPLEMENTED,
//    /** Access denied */
//    canERR_NO_ACCESS,
//    /** The license is not valid */
//    canERR_LICENSE,
//    /** Config not found */
//    canERR_REGISTRY,
//    /** Unknown error (-27) */
//    canERR_RESERVED_7,
//    /** Card not found */
//    canERR_NOCARD,
//    /** Unknown error (-25) */
//    canERR_NOCONFIGMGR,
//    /** The I/O request failed, probably due to resource shortage */
//    canERR_DRIVERFAILED,
//    /** Can not load or open the device driver */
//    canERR_DRIVERLOAD,
//    /** Unknown error (-22) */
//    canERR_RESERVED_2,
//    /** Unknown error (-21) */
//    canERR_RESERVED_6,
//    /** Unknown error (-20) */
//    canERR_RESERVED_5,
//    /** Operation not supported by hardware or firmware */
//    canERR_NOT_SUPPORTED,
//    /** Error initializing DLL or driver */
//    canERR_DYNAINIT,
//    /** DLL seems to be wrong version */
//    canERR_DYNALIB,
//    /** Can not find requested DLL */
//    canERR_DYNALOAD,
//    /** A hardware error was detected */
//    canERR_HARDWARE,
//    /** Unknown error (-14) */
//    canERR_RESERVED_1,
//    /** Transmit buffer overflow */
//    canERR_TXBUFOFL,
//    /** CAN driver type not supported */
//    canERR_DRIVER,
//    /** Unknown error (-11) */
//    canERR_INIFILE,
//    /** Handle is invalid */
//    canERR_INVHANDLE,
//    /** No more handles */
//    canERR_NOHANDLES,
//    /** Library not initialized */
//    canERR_NOTINITIALIZED,
//    /** Timeout occurred */
//    canERR_TIMEOUT,
//    /** Interrupted by signal */
//    canERR_INTERRUPTED,
//    /** No channels available */
//    canERR_NOCHANNELS,
//    /** Out of memory */
//    canERR_NOMEM,
//    /** Specified device not found */
//    canERR_NOTFOUND,
//    /** No messages available */
//    canERR_NOMSG,
    /** Internal error in the driver */
    ERR_INTERNAL,
    /**
     * Error in parameter.<br>
     * An argument in the call was invalid, out of range, not supported, etc.
     * When this error code is set there is normally also an ErrorDetail available that specifies
     * which parameter that was erroneous.
     */
    ERR_PARAM,

    /**
     * Device error.<br>
     * The device was not found, communication with device is broken, etc.
     */
    ERR_DEVICE,

    /**
     * Channel/device access error.<br>
     * No access was granted to access the channel etc.
     */
    ERR_ACCESS
  }

  /**
   * Enum containing the error details which constitute the second level error information of the
   * CanLibException.
   */
  public enum ErrorDetail {
    ILLEGAL_BITRATE,
    ILLEGAL_NUM_SAMPLING_POINTS,
    ILLEGAL_SJW,
    ILLEGAL_TSEG1,
    ILLEGAL_TSEG2,
    ILLEGAL_DLC,
    ILLEGAL_ID,
    UNSUPPORTED_DRIVER_MODE,
    COMMUNICATION_TIMEOUT,
    NON_EXISTING_CHANNEL,
    NOT_SUPPORTED,
    NULL_ARGUMENT,
    DEVICE_LOCKED,
    EXCLUSIVE_ACCESS_FAILED,
    CHANNEL_LOCKED,
    INIT_ERROR,
    INTERRUPTED_THREAD
  }

}
