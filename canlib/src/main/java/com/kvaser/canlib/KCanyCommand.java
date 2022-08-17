package com.kvaser.canlib;

import java.nio.*;

class KCanyCommand {

  public byte commandCode;
  public byte[] responseCodes;
  private KCany device;

  KCanyCommand(KCany device, byte commandCode, byte[] responseCodes) {
    this.commandCode = commandCode;
    this.responseCodes = responseCodes;
    this.device = device;
  }

  /* Subclasses should override this method if they have any parameters in the request.
   * In those cases they should call this super method to generate the header of the command.
   */
  protected ByteBuffer createRequest() {
    return createRequestHeader(new Req());
  }

  protected ByteBuffer createRequestHeader(Req req) {
    byte[] data = new byte[KCany.KCANY_CMD_SIZE];
    ByteBuffer reqData = ByteBuffer.wrap(data);
    reqData.order(ByteOrder.LITTLE_ENDIAN);

    reqData.rewind();
    reqData.put(commandCode);
    reqData.put((byte) (req.destination & 0x7f));
    reqData.putShort(req.transId);

    return reqData;
  }

  /* Subclasses should override this method if they have any parameters in the response that needs
   * to be parsed.
   */
  protected void parseResponse(ByteBuffer respData) {
    parseResponseHeader(respData, new Resp());
  }

  protected byte parseResponseHeader(ByteBuffer respData, Resp resp) {
    respData.rewind();
    byte respId = respData.get();
    byte cmdIOP = respData.get();
    short cmdIOPSeq = respData.getShort();

    resp.sourceHE =
        (byte) ((((cmdIOP & 0xC0) >>> 2) & 0x30) | (((cmdIOPSeq & 0xf000) >>> 12) & 0x0f));
    resp.transId = (short) (cmdIOPSeq & 0x0fff);
    return respId;
  }

  public void send() {
    device.SendCommand(createRequest().array());
  }

  public void sendAndWaitResponse() throws CanLibException {
    ByteBuffer reqData = createRequest();
    reqData.position(2);
    short transId = (short) (reqData.getShort() & 0x0fff);
    ByteBuffer[] respData =
        device.SendCommandAndWaitResponse(createRequest().array(), responseCodes, transId);
    for (ByteBuffer resp : respData) {
      if (resp != null) {
        parseResponse(resp);
      }
    }
  }

  class Req {

    public byte destination;
    public short transId;
  }

  class Resp {

    public byte sourceHE;
    short transId;
  }
}

/*------------------------------------------------------------------------------------------------*/
class CmdMapChannel extends KCanyCommand {

  private final static byte CMD_MAP_CHANNEL_REQ = (byte) 200;
  private final static byte CMD_MAP_CHANNEL_RESP = (byte) 201;
  public Req req = new Req();
  public Resp resp = new Resp();

  CmdMapChannel(KCany device) {
    super(device, CMD_MAP_CHANNEL_REQ, new byte[] {CMD_MAP_CHANNEL_RESP});
  }

  protected ByteBuffer createRequest() {
    ByteBuffer reqData = super.createRequestHeader(req);
    reqData.put(req.name);
    reqData.put(new byte[16 - req.name.length]);
    reqData.put(req.channel);
    return reqData;
  }

  class Req extends KCanyCommand.Req {

    byte[] name = new byte[16];
    byte channel;
  }

  class Resp extends KCanyCommand.Resp {

    int heAddress;
    int position;
    int flags;
  }

  protected void parseResponse(ByteBuffer respData) {
    parseResponseHeader(respData, resp);
    resp.heAddress = (respData.get() & 0xff);
    resp.position = (respData.get() & 0xff);
    resp.flags = (respData.getShort() & 0xffff);
  }

}

/*------------------------------------------------------------------------------------------------*/
class CmdGetCardInfo extends KCanyCommand {

  private final static byte CMD_GET_CARD_INFO_REQ = 34;
  private final static byte CMD_GET_CARD_INFO_RESP = 35;
  private final static byte CMD_GET_CARD_INFO_2 = 32;
  private final static byte CMD_USB_THROTTLE = 77;
  public Req req = new Req();
  public Resp resp = new Resp();

  CmdGetCardInfo(KCany device) {
    super(device, CMD_GET_CARD_INFO_REQ,
          new byte[] {CMD_GET_CARD_INFO_RESP, CMD_GET_CARD_INFO_2, CMD_USB_THROTTLE});
  }

  class Req extends KCanyCommand.Req {

    byte dataLevel;
  }

  class Resp extends KCanyCommand.Resp {

    long serialNumber;
    long clockResolution;
    long manufacturingDate;
    byte[] ean = new byte[8];
    byte hwRevision;
    byte usbHsMode;
    byte hwType;
    byte canTimeStampRef;
    byte channelCount;

    String pcbId;
    int oemUnlockCode;

    short usbThrottle;
  }

  protected ByteBuffer createRequest() {
    ByteBuffer reqData = super.createRequestHeader(req);
    reqData.put(req.dataLevel);
    return reqData;
  }

  protected void parseResponse(ByteBuffer respData) {

    byte respId = parseResponseHeader(respData, resp);
    switch (respId) {
      case CMD_GET_CARD_INFO_RESP:
        resp.serialNumber = ((long) respData.getInt()) & 0xffffffff;
        resp.clockResolution = ((long) respData.getInt()) & 0xffffffff;
        resp.manufacturingDate = ((long) respData.getInt()) & 0xffffffff;
        respData.get(resp.ean);
        resp.hwRevision = respData.get();
        resp.usbHsMode = respData.get();
        resp.hwType = respData.get();
        resp.canTimeStampRef = respData.get();
        resp.channelCount = respData.get();
        break;

      case CMD_GET_CARD_INFO_2:
        byte[] pcbIdBytes = new byte[24];
        char[] pcbIdChars = new char[24];
        respData.get(pcbIdBytes);
        for (int i = 0; i < pcbIdChars.length; i++) {
          pcbIdChars[i] = (char) pcbIdBytes[i];
        }
        resp.pcbId = String.valueOf(pcbIdChars);
        resp.oemUnlockCode = respData.getInt();
        break;

      case CMD_USB_THROTTLE:
        resp.usbThrottle = respData.getShort();
        break;

      default:
        // Should not happen, do nothing
        break;
    }
  }
}

/*------------------------------------------------------------------------------------------------*/
class CmdGetSoftwareInfo extends KCanyCommand {

  private final static byte CMD_GET_SOFTWARE_INFO_REQ = 38;
  private final static byte CMD_GET_SOFTWARE_INFO_RESP = 39;
  public Req req = new Req();
  public Resp resp = new Resp();

  CmdGetSoftwareInfo(KCany device) {
    super(device, CMD_GET_SOFTWARE_INFO_REQ, new byte[] {CMD_GET_SOFTWARE_INFO_RESP});
  }

  class Resp extends KCanyCommand.Resp {

    int maxOutstandingTx;
  }

  protected ByteBuffer createRequest() {
    return super.createRequestHeader(req);
  }

  protected void parseResponse(ByteBuffer respData) {
    parseResponseHeader(respData, resp);
    int reserved1 = respData.getInt();
    int reserved2 = respData.getInt();
    resp.maxOutstandingTx = (respData.getShort() & 0xffff);
  }

}

/*------------------------------------------------------------------------------------------------*/
class CmdGetSoftwareDetails extends KCanyCommand {

  public final static int SWOPTION_CONFIG_MODE = 0x01; // Memorator in config mode.
  public final static int SWOPTION_AUTO_TX_BUFFER = 0x02; // Firmware has auto tx buffers
  public final static int SWOPTION_BETA = 0x04; // Firmware is a beta release
  public final static int SWOPTION_RC = 0x08; // Firmware is a release candidate
  public final static int SWOPTION_BAD_MOOD = 0x10; // Firmware detected config error or the like
  public final static int SWOPTION_CPU_FQ_MASK = 0x60;
  public final static int SWOPTION_80_MHZ_CLK = 0x20; // hires timers run at 80 MHZ
  public final static int SWOPTION_24_MHZ_CLK = 0x40; // hires timers run at 24 MHZ

  private final static byte CMD_GET_SOFTWARE_DETAILS_REQ = (byte) 202;
  private final static byte CMD_GET_SOFTWARE_DETAILS_RESP = (byte) 203;
  public Req req = new Req();
  public Resp resp = new Resp();

  CmdGetSoftwareDetails(KCany device) {
    super(device, CMD_GET_SOFTWARE_DETAILS_REQ, new byte[] {CMD_GET_SOFTWARE_DETAILS_RESP});
  }

  class Resp extends KCanyCommand.Resp {

    int swOptions;
    int swVersion;
    int swName;
    int[] ean = new int[2];
    int maxBitrate;
  }

  protected ByteBuffer createRequest() {
    return super.createRequestHeader(req);
  }

  protected void parseResponse(ByteBuffer respData) {
    parseResponseHeader(respData, resp);
    resp.swOptions = respData.getInt();
    resp.swVersion = respData.getInt();
    resp.swName = respData.getInt();
    resp.ean[0] = respData.getInt();
    resp.ean[1] = respData.getInt();
    resp.maxBitrate = respData.getInt();
  }

}

/*------------------------------------------------------------------------------------------------*/
class CmdAutoTxBuffer extends KCanyCommand {

  public final static byte AUTOTXBUFFER_CMD_GET_INFO = 1;       // Get implementation information
  public final static byte AUTOTXBUFFER_CMD_CLEAR_ALL = 2;      // Clear all buffers on a channel
  public final static byte AUTOTXBUFFER_CMD_ACTIVATE = 3;       // Activate a specific buffer
  public final static byte AUTOTXBUFFER_CMD_DEACTIVATE = 4;     // Deactivate a specific buffer
  public final static byte AUTOTXBUFFER_CMD_SET_INTERVAL = 5;   // Set tx buffer transmit interval
  public final static byte AUTOTXBUFFER_CMD_GENERATE_BURST = 6; // Generate a burst of messages
  public final static byte AUTOTXBUFFER_CMD_SET_MSG_COUNT = 7;  // Set tx buffer message count
  public final static byte AUTOTXBUFFER_CMD_SET_BUFFER = 8;     // Set tx buffer message
  private final static short ZERO_SHORT = 0;

  private final static byte CMD_AUTO_TX_BUFFER_REQ = 72;
  private final static byte CMD_AUTO_TX_BUFFER_RESP = 73;
  public Req req = new Req();
  public Resp resp = new Resp();

  CmdAutoTxBuffer(KCany device) {
    super(device, CMD_AUTO_TX_BUFFER_REQ, new byte[] {CMD_AUTO_TX_BUFFER_RESP});
  }

  class Req extends KCanyCommand.Req {

    int interval;
    byte requestType;
    byte bufNo;
    int id;
    byte[] data = new byte[8];
    byte dlc;
    byte flags;
  }

  class Resp extends KCanyCommand.Resp {

    byte responseType;
    short bufferCount;
    int capabilities;
    int timerResolution;
    int status;
  }

  protected ByteBuffer createRequest() {
    ByteBuffer reqData = super.createRequestHeader(req);
    reqData.putInt(req.interval);
    reqData.put(req.requestType);
    reqData.put(req.bufNo);
    reqData.putShort(ZERO_SHORT);
    reqData.putInt(req.id);
    reqData.put(req.data);
    reqData.put(req.dlc);
    reqData.put(req.flags);
    return reqData;
  }

  protected void parseResponse(ByteBuffer respData) {
    parseResponseHeader(respData, resp);
    resp.responseType = respData.get();
    resp.bufferCount = (short) (respData.get() & 0x00ff);
    resp.capabilities = respData.getShort() & 0x0000ffff;
    resp.timerResolution = respData.getInt();
    resp.status = respData.getInt();
  }

}

/*------------------------------------------------------------------------------------------------*/
class CmdSetDriverMode extends KCanyCommand {

  private final static byte CMD_SET_DRIVERMODE_REQ = 21;
  public Req req = new Req();
  public Resp resp = new Resp();

  CmdSetDriverMode(KCany device) {
    super(device, CMD_SET_DRIVERMODE_REQ, null);
  }

  class Req extends KCanyCommand.Req {

    byte driverMode;
  }

  protected ByteBuffer createRequest() {
    ByteBuffer reqData = super.createRequestHeader(req);
    reqData.put(req.driverMode);
    return reqData;
  }

  public void sendAndWaitResponse() {
    // No response defined, override the super class' wait method
    super.send();
  }

}

/*------------------------------------------------------------------------------------------------*/
class CmdGetDriverMode extends KCanyCommand {

  private final static byte CMD_GET_DRIVERMODE_REQ = 22;
  private final static byte CMD_GET_DRIVERMODE_RESP = 23;
  public Req req = new Req();
  public Resp resp = new Resp();

  CmdGetDriverMode(KCany device) {
    super(device, CMD_GET_DRIVERMODE_REQ, new byte[] {CMD_GET_DRIVERMODE_RESP});
  }

  class Req extends KCanyCommand.Req {

    byte channel;
  }

  class Resp extends KCanyCommand.Resp {

    byte driverMode;
    byte channel;
  }

  protected ByteBuffer createRequest() {
    ByteBuffer reqData = super.createRequestHeader(req);
    reqData.put(req.channel);
    return reqData;
  }

  protected void parseResponse(ByteBuffer respData) {
    parseResponseHeader(respData, resp);
    resp.driverMode = respData.get();
    resp.channel = respData.get();
  }

}

/*------------------------------------------------------------------------------------------------*/
class CmdTxCanMessage extends KCanyCommand {

  public final static byte CMD_TX_ACKNOWLEDGE = 50;
  private final static byte CMD_TX_CAN_MESSAGE = 33;
  public Req req = new Req();
  public Resp resp = new Resp();

  CmdTxCanMessage(KCany device) {
    super(device, CMD_TX_CAN_MESSAGE, null);
  }

  class Resp extends KCanyCommand.Resp {

    int id;
    byte[] data = new byte[8];
    byte dlc;
    byte flags;
    int[] time = new int[3];
  }

  class Req extends KCanyCommand.Req {

    int id;
    byte[] data;
    byte dlc;
    byte flags;
    short transId;
    byte channel;
  }

  protected ByteBuffer createRequest() {
    ByteBuffer reqData = super.createRequestHeader(req);
    reqData.putInt(req.id);
    reqData.put(req.data);
    reqData.put(req.dlc);
    reqData.put(req.flags);
    reqData.putShort(req.transId);
    reqData.put(req.channel);
    return reqData;
  }

  public void parseResponse(ByteBuffer respData) {
    parseResponseHeader(respData, resp);
    resp.id = respData.getInt();
    respData.get(resp.data);
    resp.dlc = respData.get();
    resp.flags = respData.get();
    resp.time[0] = respData.getShort() & 0xffff;
    resp.time[1] = respData.getShort() & 0xffff;
    resp.time[2] = respData.getShort() & 0xffff;
  }
}

/*------------------------------------------------------------------------------------------------*/
class CmdLogMessage extends KCanyCommand {

  public final static byte CMD_LOG_MESSAGE = 106;

  public Req req = new Req();
  public Resp resp = new Resp();

  CmdLogMessage(KCany device) {
    super(device, (byte) 0, new byte[] {CMD_LOG_MESSAGE});
  }

  class Resp extends KCanyCommand.Resp {

    byte cmdLen;
    byte cmdNo;
    byte channel;
    byte flags;
    int[] time = new int[3];
    byte dlc;
    int id;
    byte[] data = new byte[8];
  }

  public void send() {
    // Shall never be sent
  }

  public void sendAndWaitResponse() {
    // Shall never be sent
  }

  public void parseResponse(ByteBuffer respData) {
    parseResponseHeader(respData, resp);
    resp.cmdLen = respData.get();
    resp.cmdNo = respData.get();
    resp.channel = respData.get();
    resp.flags = respData.get();
    resp.time[0] = respData.getShort() & 0xffff;
    resp.time[1] = respData.getShort() & 0xffff;
    resp.time[2] = respData.getShort() & 0xffff;
    resp.dlc = respData.get();
    byte padding = respData.get();
    resp.id = respData.getInt();
    respData.get(resp.data);
  }
}

/*------------------------------------------------------------------------------------------------*/
class CmdSetBusParams extends KCanyCommand {

  private final static byte CMD_SET_BUSPARAMS_REQ = 16;
  private final static byte CMD_SET_BUSPARAMS_RESP = 85;
  public Req req = new Req();
  public Resp resp = new Resp();

  CmdSetBusParams(KCany device) {
    super(device, CMD_SET_BUSPARAMS_REQ, new byte[] {CMD_SET_BUSPARAMS_RESP});
  }

  class Req extends KCanyCommand.Req {

    CanBusParams busParams;
    byte channel;
  }

  protected ByteBuffer createRequest() {
    ByteBuffer reqData = super.createRequestHeader(req);
    if (req.busParams != null) {
      reqData.putInt((int) (req.busParams.bitRate & 0xffffffff));
      reqData.put((byte) (req.busParams.tseg1 & 0xff));
      reqData.put((byte) (req.busParams.tseg2 & 0xff));
      reqData.put((byte) (req.busParams.sjw & 0xff));
      reqData.put((byte) (req.busParams.numSamplingPoints & 0xff));
      reqData.put(req.channel);
    }
    return reqData;
  }

}

/*------------------------------------------------------------------------------------------------*/
class CmdGetBusParams extends KCanyCommand {

  private final static byte CMD_GET_BUSPARAMS_REQ = 17;
  private final static byte CMD_GET_BUSPARAMS_RESP = 18;
  public Req req = new Req();
  public Resp resp = new Resp();

  CmdGetBusParams(KCany device) {
    super(device, CMD_GET_BUSPARAMS_REQ, new byte[] {CMD_GET_BUSPARAMS_RESP});
  }

  class Req extends KCanyCommand.Req {

    byte paramType;
  }

  class Resp extends KCanyCommand.Resp {

    CanBusParams busParams = new CanBusParams();
    byte channel;
  }

  protected ByteBuffer createRequest() {
    ByteBuffer reqData = super.createRequestHeader(req);
    reqData.put(req.paramType);
    return reqData;
  }

  protected void parseResponse(ByteBuffer respData) {
    parseResponseHeader(respData, resp);
    resp.busParams.bitRate = respData.getInt();
    resp.busParams.tseg1 = respData.get();
    resp.busParams.tseg2 = respData.get();
    resp.busParams.sjw = respData.get();
    int numberOfSamplingPoints = respData.get();
    resp.channel = respData.get();
  }

}

/*------------------------------------------------------------------------------------------------*/
class CmdBusOn extends KCanyCommand {

  private final static byte CMD_START_CHIP_REQ = 26;
  private final static byte CMD_START_CHIP_RESP = 27;
  public Req req = new Req();
  public Resp resp = new Resp();

  CmdBusOn(KCany device) {
    super(device, CMD_START_CHIP_REQ, new byte[] {CMD_START_CHIP_RESP});
  }

  protected ByteBuffer createRequest() {
    ByteBuffer reqData = super.createRequestHeader(req);
    return reqData;
  }
}

/*------------------------------------------------------------------------------------------------*/
class CmdBusOff extends KCanyCommand {

  private final static byte CMD_STOP_CHIP_REQ = 28;
  private final static byte CMD_STOP_CHIP_RESP = 29;
  public Req req = new Req();
  public Resp resp = new Resp();

  CmdBusOff(KCany device) {
    super(device, CMD_STOP_CHIP_REQ, new byte[] {CMD_STOP_CHIP_RESP});
  }

  protected ByteBuffer createRequest() {
    ByteBuffer reqData = super.createRequestHeader(req);
    return reqData;
  }
}

/*------------------------------------------------------------------------------------------------*/
class CmdLedAction extends KCanyCommand {

  public final static byte LED_SUBCOMMAND_ALL_LEDS_ON = 0;
  public final static byte LED_SUBCOMMAND_ALL_LEDS_OFF = 1;
  public final static byte LED_SUBCOMMAND_LED_0_ON = 2;
  public final static byte LED_SUBCOMMAND_LED_0_OFF = 3;
  public final static byte LED_SUBCOMMAND_LED_1_ON = 4;
  public final static byte LED_SUBCOMMAND_LED_1_OFF = 5;
  public final static byte LED_SUBCOMMAND_LED_2_ON = 6;
  public final static byte LED_SUBCOMMAND_LED_2_OFF = 7;
  public final static byte LED_SUBCOMMAND_LED_3_ON = 8;
  public final static byte LED_SUBCOMMAND_LED_3_OFF = 9;
  public final static byte LED_SUBCOMMAND_LED_4_ON = 10;
  public final static byte LED_SUBCOMMAND_LED_4_OFF = 11;
  public final static byte LED_SUBCOMMAND_LED_5_ON = 12;
  public final static byte LED_SUBCOMMAND_LED_5_OFF = 13;
  public final static byte LED_SUBCOMMAND_LED_6_ON = 14;
  public final static byte LED_SUBCOMMAND_LED_6_OFF = 15;
  private final static byte CMD_LED_ACTION_REQ = 101;
  private final static byte CMD_LED_ACTION_RESP = 102;
  public Req req = new Req();
  public Resp resp = new Resp();

  CmdLedAction(KCany device) {
    super(device, CMD_LED_ACTION_REQ, new byte[] {CMD_LED_ACTION_RESP});
  }

  class Req extends KCanyCommand.Req {

    byte subCommand;
    short timeout;
  }

  class Resp extends KCanyCommand.Resp {

    byte subCommand;
  }

  protected ByteBuffer createRequest() {
    ByteBuffer reqData = super.createRequestHeader(req);
    reqData.put(req.subCommand);
    reqData.put((byte) 0);
    reqData.putShort(req.timeout);
    return reqData;
  }

  protected void parseResponse(ByteBuffer respData) {
    parseResponseHeader(respData, resp);
    resp.subCommand = respData.get();
  }

}

/*------------------------------------------------------------------------------------------------*/
class CmdChipState extends KCanyCommand {

  public final static byte CMD_GET_CHIP_STATE_REQ = 19;
  public final static byte CMD_CHIP_STATE_EVENT = 20;
  public final static byte BUS_STATUS_BUS_RESET_MASK = 0x01;
  public final static byte BUS_STATUS_BUS_ERROR_MASK = 0x10;
  public final static byte BUS_STATUS_BUS_PASSIVE_MASK = 0x20;
  public final static byte BUS_STATUS_BUS_OFF_MASK = 0x40;

  public Req req = new Req();
  public Resp resp = new Resp();

  CmdChipState(KCany device) {
    super(device, CMD_GET_CHIP_STATE_REQ, new byte[] {CMD_CHIP_STATE_EVENT});
  }

  class Resp extends KCanyCommand.Resp {

    int[] time = new int[3];
    byte txErrorCounter;
    byte rxErrorCounter;
    byte busStatus;
    byte channel;
  }

  public void parseResponse(ByteBuffer respData) {
    parseResponseHeader(respData, resp);
    resp.time[0] = respData.getShort() & 0xffff;
    resp.time[1] = respData.getShort() & 0xffff;
    resp.time[2] = respData.getShort() & 0xffff;
    resp.txErrorCounter = respData.get();
    resp.rxErrorCounter = respData.get();
    resp.busStatus = respData.get();
    resp.channel = respData.get();
  }
}

// ------- COMMAND TEMPLATE --------
// Simple command with request and response parameters, one response only
///*------------------------------------------------------------------------------------------------*/
//class CmdTheCommandName extends KCanyCommand {
//
//  private final static byte CMD_THE_COMMAND_REQ = (byte) 0;
//  private final static byte CMD_THE_COMMAND_RESP = (byte) 1;
//  public Req req = new Req();
//  public Resp resp = new Resp();
//
//  CmdTheCommandName(KCany device) {
//    super(device, CMD_THE_COMMAND_REQ, new byte[] {CMD_THE_COMMAND_RESP});
//  }
//
//  protected ByteBuffer createRequest() {
//    ByteBuffer reqData = super.createRequestHeader(req);
//    reqData.put(req.xxxx);
//    return reqData;
//  }
//
//  class Req extends KCanyCommand.Req {
//
//    byte xxxx;
//  }
//
//  class Resp extends KCanyCommand.Resp {
//
//    int yyyy;
//  }
//
//  protected void parseResponse(ByteBuffer respData) {
//    parseResponseHeader(respData, resp);
//    resp.yyyy = respData.get();
//  }
//
//}

// List of not yet implemented commands:
//private final static byte CMD_RX_STD_MESSAGE = 12;
//private final static byte CMD_RX_EXT_MESSAGE = 14;
//private final static byte CMD_RESET_CHIP_REQ = 24;
//private final static byte CMD_RESET_CARD_REQ = 25;
//private final static byte CMD_READ_CLOCK_REQ = 30;
//private final static byte CMD_READ_CLOCK_RESP = 31;
//// 33 may be used - NOT see CMD_TX_CAN_MESSAGE
//private final static byte CMD_GET_INTERFACE_INFO_REQ = 36;
//private final static byte CMD_GET_INTERFACE_INFO_RESP = 37;
//private final static byte CMD_GET_BUSLOAD_REQ = 40;
//private final static byte CMD_GET_BUSLOAD_RESP = 41;
//private final static byte CMD_RESET_STATISTICS = 42;
//private final static byte CMD_CHECK_LICENSE_REQ = 43;
//private final static byte CMD_CHECK_LICENSE_RESP = 44;
//private final static byte CMD_ERROR_EVENT = 45;
//// 46, 47 reserved
//private final static byte CMD_FLUSH_QUEUE = 48;
//private final static byte CMD_RESET_ERROR_COUNTER = 49;
//private final static byte CMD_TX_ACKNOWLEDGE = 50;
//private final static byte CMD_CAN_ERROR_EVENT = 51;
//private final static byte CMD_MEMO_GET_DATA = 52;
//private final static byte CMD_MEMO_PUT_DATA = 53;
//private final static byte CMD_MEMO_PUT_DATA_START = 54;
//private final static byte CMD_MEMO_ASYNCOP_START = 55;
//private final static byte CMD_MEMO_ASYNCOP_GET_DATA = 56;
//private final static byte CMD_MEMO_ASYNCOP_CANCEL = 57;
//private final static byte CMD_MEMO_ASYNCOP_FINISHED = 58;
//private final static byte CMD_DISK_FULL_INFO = 59;
//private final static byte CMD_TX_REQUEST = 60;
//private final static byte CMD_SET_HEARTBEAT_RATE_REQ = 61;
//private final static byte CMD_HEARTBEAT_RESP = 62;
//private final static byte CMD_SET_AUTO_TX_BUFFER = 63;
//private final static byte CMD_GET_EXTENDED_INFO = 64;
//private final static byte CMD_TCP_KEEPALIVE = 65;
//private final static byte CMD_FLUSH_QUEUE_RESP = 66;
//private final static byte CMD_HYDRA_TX_INTERVAL_REQ = 67;
//private final static byte CMD_HYDRA_TX_INTERVAL_RESP = 68;
//private final static byte CMD_SET_BUSPARAMS_FD_REQ = 69;
//private final static byte CMD_SET_BUSPARAMS_FD_RESP = 70;
//// 71 can be reused
//private final static byte CMD_SET_TRANSCEIVER_MODE_REQ = 74;
//private final static byte CMD_TREF_SOFNR = 75;
//private final static byte CMD_SOFTSYNC_ONOFF = 76;
//private final static byte CMD_SOUND = 78;
//private final static byte CMD_LOG_TRIG_STARTUP = 79;
//private final static byte CMD_SELF_TEST_REQ = 80;
//private final static byte CMD_SELF_TEST_RESP = 81;
//// 82-84 can be reused
//private final static byte CMD_SET_IO_PORTS_REQ = 86;
//private final static byte CMD_GET_IO_PORTS_REQ = 87;
//private final static byte CMD_GET_IO_PORTS_RESP = 88;
//// 89-96 can be used
//private final static byte CMD_GET_TRANSCEIVER_INFO_REQ = 97;
//private final static byte CMD_GET_TRANSCEIVER_INFO_RESP = 98;
//private final static byte CMD_MEMO_CONFIG_MODE = 99;
//// 100 can be used
//private final static byte CMD_INTERNAL_DUMMY = 103;
//private final static byte CMD_READ_USER_PARAMETER = 104;
//private final static byte CMD_MEMO_CPLD_PRG = 105;
//private final static byte CMD_LOG_TRIG = 107;
//private final static byte CMD_LOG_RTC_TIME = 108;
//// 109 - 118 reserved
//private final static byte CMD_IMP_KEY = 119;
//private final static byte CMD_PRINTF = 120;
//private final static byte RES_PRINTF = (byte) (CMD_PRINTF + 128);
//private final static byte TRP_DATA = 121;
//private final static byte CMD_REGISTER_HE_REQ = 122;
//private final static byte CMD_REGISTER_HE_RESP = 123;
//private final static byte CMD_QUERY_ADDR_HE_REQ = 124;
//private final static byte CMD_QUERY_ADDR_HE_RESP = 125;
//private final static byte CMD_LISTEN_TO_HE_REQ = 126;
//private final static byte CMD_LISTEN_TO_HE_RESP = 127;
//private final static byte CMD_QUERY_NEXT_HE_REQ = (byte) 128;
//private final static byte CMD_QUERY_NEXT_HE_RESP = (byte) 129;
//private final static byte CMD_MEMORY_READ_REQ = (byte) 130;
//private final static byte CMD_MEMORY_READ_RESP = (byte) 131;
//private final static byte CMD_MEMORY_WRITE_REQ = (byte) 132;
//private final static byte CMD_MEMORY_WRITE_RESP = (byte) 133;
//private final static byte CMD_MEMORY_SEARCH_REQ = (byte) 134;
//private final static byte CMD_MEMORY_SEARCH_RESP = (byte) 135;
//private final static byte CMD_MEASURE = (byte) 136;
//private final static byte CMD_FATAL_ERROR = (byte) 137;
//private final static byte CMD_LOG_ACTION = (byte) 138;
//private final static byte CMD_IO_TRIG_REQ = (byte) 148;
//private final static byte CMD_IO_TRIG_RESP = (byte) 149;
//private final static byte CMD_IO_TRIG_MSG = (byte) 150;
//private final static byte CMD_IO_PORT_INFO_REQ = (byte) 151;
//private final static byte CMD_IO_PORT_INFO_RESP = (byte) 152;
//private final static byte CMD_IO_PORT_CTRL_REQ = (byte) 153;
//private final static byte CMD_IO_PORT_CTRL_RESP = (byte) 154;
//private final static byte CMD_GET_FILE_COUNT_REQ = (byte) 158;
//private final static byte CMD_GET_FILE_COUNT_RESP = (byte) 159;
//private final static byte CMD_GET_FILE_NAME_REQ = (byte) 160;
//private final static byte CMD_GET_FILE_NAME_RESP = (byte) 161;
//private final static byte CMD_GET_NETWORK_DEVICE_NAME_REQ = (byte) 162;
//private final static byte CMD_GET_NETWORK_DEVICE_NAME_RESP = (byte) 163;
//private final static byte CMD_DEVICE_PING_REQ = (byte) 164;
//private final static byte CMD_DEVICE_PING_RESP = (byte) 165;
//private final static byte CMD_SET_DEVICE_MODE = (byte) 204;
//private final static byte CMD_GET_DEVICE_MODE = (byte) 205;
