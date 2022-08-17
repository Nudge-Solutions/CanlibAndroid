package com.kvaser.canlib;

import android.os.*;
import android.util.*;

import java.nio.*;
import java.text.*;
import java.util.*;

import com.kvaser.canlib.CanLibException.ErrorDetail;
import com.kvaser.canlib.CanLibException.ErrorCode;

class KCanl implements KvDeviceInterface {

  private final static boolean debugInLogcat = false;

  // The response timeout in number of 100 ms
  private final static int LEAF_CMD_RESPONSE_WAIT_TIME = 20;

  private final UsbCanlDeviceHandle usbHandle;
  private final KvDevices           deviceType;

  private byte          hwType;
  private final Ean     ean = new Ean();
  private long          serialNumber;
  private int           firmwareVersionMajor;
  private int           firmwareVersionMinor;
  private int           firmwareVersionBuild;
  private byte          hwRevision;
  private final  String pcbId = "";

  private long manufacturingDate;
  private byte channelCount;

  private boolean cardRefuseToUseCan = false;        // Major problem detected
  private boolean cardFirmwareBeta = false;          // Firmware is beta
  private boolean cardFirmwareRc = false;            // Firmware is release candidate
  private boolean cardAutoRespObjectBuffers = false; // Firmware supports auto-resp object buffers
  private boolean cardAutoTxObjectBuffers = false;   // Firmware supports periodic tx object buffers

  private int hiresTimerFq = 1;
  private boolean timeoffsetValid = false;

  private final List<CanChannelEventListener> canChannelListeners = new ArrayList<>();

  private final List<WaitNode> waitList = new ArrayList<>();

  private final long numberOfBitsFromAckToValidMsg = 8;

  private int[]                 autoTxBufferCount;
  private int[]                 autoTxBufferResolution;
  private final byte[]          nextTransId;
  private long[]                timestampAdjustment;
  private long[]                bitrate;
  private CanMessage[][]        sentMsgs;                // Tx messages sorted by transaction id

  KCanl(UsbCanlDeviceHandle usbHandle, KvDevices deviceType) throws CanLibException {
    this.usbHandle = usbHandle;
    this.deviceType = deviceType;

    usbHandle.addListener(this);

    GetCardInfo();
    GetSoftwareDetails();

    autoTxBufferCount      = new int[channelCount];
    autoTxBufferResolution = new int[channelCount];
    nextTransId            = new byte[channelCount];
    timestampAdjustment    = new long[channelCount];
    bitrate                = new long[channelCount];
    sentMsgs               = new CanMessage[channelCount][256];

    Arrays.fill(nextTransId, (byte) 1);
    Arrays.fill(timestampAdjustment, 0);
    Arrays.fill(bitrate, 125000);

    // Do an initial setup of each channel. For a normal usage this should probably not be
    // necessary but the test code fails if not. The test code probably relies on the
    // defaults for a hydra chip. Setting up here makes the changes to the test code minimal.
    for (byte i = 0; i < channelCount; i++ ) SendCommand(new ResetChipReq(i).data);
    try {
      Thread.sleep(100);
    }
    catch (Exception e) {
      throw new CanLibException(ErrorCode.ERR_INTERNAL,
                                ErrorDetail.INTERRUPTED_THREAD,
                                e.getMessage());
    }

    CanBusParams busParams = new CanBusParams();
    for (byte i = 0; i < channelCount; i++ ) {
      setBusParams(i, busParams);
      setBusOutputControl(i, CanDriverType.NORMAL);
    }

    // Auto Tx Buffer is set up here in KCany, should we do the same?
    // Leave the issue to future changes. At the moment we just get the information
    if (cardAutoTxObjectBuffers) {
      for (byte i = 0; i < channelCount; i++) {
        GetAutoTxInfo(i);
      }
    }
  }

  @Override
  public void close() {
    CanChannelAccess.releaseAccessPerDevice("" + (int) serialNumber, ean.getEanString(), channelCount);

    if (usbHandle != null) {
      usbHandle.close();
    }
  }

  @Override
  public int getNumberOfChannels() {
    return channelCount;
  }

  @Override
  public boolean isVirtual() {
    return false;
  }

  @Override
  public void setBusParams(int channelIndex, CanBusParams busParams) {
    SetBusParamsReq req = new SetBusParamsReq((byte) channelIndex, busParams);
    SendCommand(req.data);
    // Unless we assume that this command succeed the timestamp will be wrong.
    updateTimestampAdjustment(channelIndex, busParams.bitRate);
  }

  @Override
  public CanBusParams getBusParams(int channelIndex) throws CanLibException {
    GetBusParamsReq request = new GetBusParamsReq((byte)channelIndex);
    byte[] respCmds = {GetBusParamsResp.RespId};

    ByteBuffer[] buffer = SendCommandAndWaitResponse(request.data, respCmds);
    GetBusParamsResp response = new GetBusParamsResp(buffer[0]);

    // If we get here we assume that the command was successful.
    updateTimestampAdjustment(channelIndex, response.busParams.bitRate);

    return response.busParams;
  }

  @Override
  public void busOn(int channelIndex) throws CanLibException {
    StartChipReq req = new StartChipReq((byte) channelIndex);
    byte[] respCmds = {StartChipResp.RespId};
    // We don't have any use for the response so just discard it. It's enough just to wait for it
    SendCommandAndWaitResponse(req.data, respCmds);
  }

  @Override
  public void busOff(int channelIndex) throws CanLibException {
    StopChipReq req = new StopChipReq((byte) channelIndex);
    byte[] respCmds = {StopChipResp.RespId};
    // We don't have any use for the response so just discard it. It's enough just to wait for it
    SendCommandAndWaitResponse(req.data, respCmds);
  }

  @Override
  public void setBusOutputControl(int channelIndex,
                                  CanDriverType driverType) throws CanLibException {
    SetDriverModeReq req = new SetDriverModeReq((byte) channelIndex, driverType);
    SendCommand(req.data);
  }

  @Override
  public CanDriverType getBusOutputControl(int channelIndex) throws CanLibException {
    GetDriverModeReq req = new GetDriverModeReq((byte) channelIndex);
    byte[] respCmds = {GetDriverModeResp.RespId};
    ByteBuffer[] buffer = SendCommandAndWaitResponse(req.data, respCmds);
    GetDriverModeResp resp = new GetDriverModeResp(buffer[0]);
    return resp.ctrlMode;
  }

  @Override
  public void write(int channelIndex, CanMessage msg) throws CanLibException {
    TxReq req = new TxReq((byte) channelIndex, msg);
    sentMsgs[channelIndex][(int)req.tId & 0xFF] = new CanMessage(msg);
    SendCommand(req.data);
  }

  @Override
  public Bundle getDeviceInfo() {
    Bundle bundle = new Bundle();
    bundle.putString("Device Name", deviceType.getDeviceName());
    bundle.putString("Hardware Type", Byte.toString(hwType));
    bundle.putString("Manufacturer", "Kvaser AB");
    bundle.putString("Card EAN", ean.getEanString());
    bundle.putString("Product Number", ean.getProductNumber());
    bundle.putString("Serial Number", Long.toString(serialNumber));
    bundle.putString("Firmware Version", firmwareVersionMajor + "." + firmwareVersionMinor + "."
                                         + firmwareVersionBuild);
    bundle.putString("Hardware Revision", Byte.toString(hwRevision));
    DateFormat dateFormat = DateFormat.getDateInstance();
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    bundle.putString("Mfg Date", dateFormat.format(new Date(manufacturingDate * 1000)));
    bundle.putString("PCB revision", pcbId);

    return bundle;
  }

  @Override
  public Ean getEan() {
    return ean;
  }

  @Override
  public int getSerialNumber() {
    return (int)serialNumber;
  }

  @Override
  public void flashLeds() {
    // The current firmware for leaf is broken so controlling leds doesn't work.
    // This code is left behind to enable one blink from the leds.
    LEDActionReq req = new LEDActionReq((byte)0, (short)0);
    byte[] respCmds = {LEDActionResp.RespId};
    for (int i = 0; i < 5; i++) {
      try {
        ByteBuffer[] buffer = SendCommandAndWaitResponse(req.data, respCmds);
        LEDActionResp resp = new LEDActionResp(buffer[0]);
        Thread.sleep(200);
      } catch (Exception e) {
        debugLog(e.getMessage());
      }
    }
  }

  @Override
  public void registerCanChannelEventListener(CanChannelEventListener listener) {
    canChannelListeners.add(listener);
  }

  @Override
  public void unregisterCanChannelEventListener(CanChannelEventListener listener) {
    canChannelListeners.remove(listener);
  }

  @Override
  public void UsbDataReceived(byte[] bytes) {

    try {
      boolean processed = HandleUsbCommand(bytes);

      // Notify all waiters of this response
      synchronized (waitList) {
        for (int i = 0; i < waitList.size(); i++) {
          synchronized (waitList.get(i)) {
            if ((waitList.get(i).responseCmd == bytes[1]) &&
                (waitList.get(i).ignoreTransId || (waitList.get(i).transId == bytes[2]))) {
              waitList.get(i).responseReceived = true;
              waitList.get(i).receivedData = bytes;
              waitList.get(i).notifyAll();

            } else {
              if (!processed) {
                if (waitList.get(i).ignoreTransId) {
                  debugLog("Un-processed package " + bytes[1] + " with length " + bytes[0]);
                }
                else {
                  debugLog("Un-processed package " + bytes[1] + " with length " + bytes[0]
                           + " and transId " + bytes[2]);
                }
              }
            }
          }
        }
      }
    } catch (CanLibException e) {
      debugLog(e.getMessage());
    }
  }

  private boolean HandleUsbCommand(byte[] data) throws CanLibException {

    boolean result = false;
    ByteBuffer buffer = ByteBuffer.wrap(data);
    buffer.order(ByteOrder.LITTLE_ENDIAN);

    buffer.get(); // receivedMsgLen
    byte receivedRespId = buffer.get();

    switch (receivedRespId) {
      case TxAcknowledge.RespId:
        result = true;
        TxAcknowledge txAcknowledge = new TxAcknowledge(buffer);

        CanMessage txAckMsg = sentMsgs[txAcknowledge.channel][(int)txAcknowledge.tId & 0xFF];
        txAckMsg.flags.add(CanMessage.MessageFlags.TX_ACK);
        txAckMsg.time = txAcknowledge.time;
        txAckMsg.direction = CanMessage.Direction.TX;
        for (CanChannelEventListener listener : canChannelListeners) {
          if (listener.getChannelIndex() == txAcknowledge.channel) {
            listener.canChannelEvent(CanChannelEventListener.CanChannelEventType.MESSAGE, txAckMsg);
          }
        }
        break;

      case ChipStateResp.RespId:
        result = true;
        ChipStateResp chipStateResp = new ChipStateResp(buffer);

        for (CanChannelEventListener listener : canChannelListeners) {
          if (listener.getChannelIndex() == chipStateResp.chipState.channel) {
            listener.canChannelEvent(CanChannelEventListener.CanChannelEventType.CHIP_STATE,
                                     chipStateResp.chipState);
          }
        }
        break;

      case RxLogMessage.RespId:
        result = true;
        RxLogMessage logMsg = new RxLogMessage(buffer);
        for (CanChannelEventListener listener : canChannelListeners) {
          if (listener.getChannelIndex() == logMsg.channel) {
            listener.canChannelEvent(CanChannelEventListener.CanChannelEventType.MESSAGE, logMsg.canMsg);
          }
        }
        break;

      case RxMessage.RespIdStd:
      case RxMessage.RespIdExt:
        result = true;
        RxMessage rxMsg = new RxMessage(buffer);
        for (CanChannelEventListener listener : canChannelListeners) {
          if (listener.getChannelIndex() == rxMsg.channel) {
            listener.canChannelEvent(CanChannelEventListener.CanChannelEventType.MESSAGE, rxMsg.canMsg);
          }
        }
        break;

      case ErrorEvent.RespId:
        result = true;
        if (BuildConfig.BUILD_TYPE.equals("debug")) {
          // Unhandled for now but handy breakpoint location if needed
          ErrorEvent errorEvent = new ErrorEvent(buffer);
        }
        break;

      case CanErrorEvent.RespId:
        result = true;
        if (BuildConfig.BUILD_TYPE.equals("debug")) {
          // Unhandled for now but handy breakpoint location if needed
          CanErrorEvent canErrorEvent = new CanErrorEvent(buffer);
        }
        break;

      // We're ignoring these messages for now
      // Flag them as handled to avoid them being sent to debugLog in UsbDataReceived
      case 32:   //CARD_INFO2
      case 77:   //USB_THROTTLE
        result = true;
        break;

      case TxRequest.RespId:
        result = true;
        if (BuildConfig.BUILD_TYPE.equals("debug")) {
          // Unhandled for now but handy breakpoint location if needed
          TxRequest txRequest = new TxRequest(buffer);
        }
        break;
    }

    return result;
  }

  private ByteBuffer[] SendCommandAndWaitResponse(byte[] requestData, byte[] responseCmd) throws CanLibException {
    return SendCommandAndWaitResponse(requestData, responseCmd, false);
  }

  private ByteBuffer[] SendCommandAndWaitResponse(byte[] requestData, byte[] responseCmd, boolean ignoreTransId) throws CanLibException {
    WaitNode[] waitNodes = new WaitNode[responseCmd.length];
    for (int i = 0; i < waitNodes.length; i++) {
      waitNodes[i] = new WaitNode(responseCmd[i], requestData[2], ignoreTransId);
      synchronized (waitList) {
        waitList.add(waitNodes[i]);
      }
    }
    ByteBuffer[] respData = new ByteBuffer[responseCmd.length];
    debugLog("Req", requestData);
    for (int i = 0; i < waitNodes.length; i++) {

      synchronized (waitNodes[i]) {
        if (i == 0) {
          usbHandle.send((requestData));
        }
        int timeoutCounter = 0;
        while ((!waitNodes[i].responseReceived) && (timeoutCounter < LEAF_CMD_RESPONSE_WAIT_TIME)) {
          try {
            waitNodes[i].wait(100);
          } catch (InterruptedException e) {
            throw new CanLibException(ErrorCode.ERR_INTERNAL,
                                      ErrorDetail.INTERRUPTED_THREAD,
                                      "Thread was interrupted while waiting for response from device");
          }
          timeoutCounter++;
        }
        if (timeoutCounter == LEAF_CMD_RESPONSE_WAIT_TIME) {
          if (waitNodes[i].ignoreTransId) {
            debugLog("Timeout occurred while waiting for response cmd " +
                     (((int) waitNodes[i].responseCmd) & 0xFF));
          }
          else {
            debugLog("Timeout occurred while waiting for response cmd " +
                     (((int) waitNodes[i].responseCmd) & 0xFF) +
                     " (transId " + waitNodes[i].transId + ")");
          }
        }
      }

      synchronized (waitList) {
        waitList.remove(waitNodes[i]);
      }
      synchronized (waitNodes[i]) {
        if (waitNodes[i].responseReceived) {
          respData[i] = ByteBuffer.wrap(waitNodes[i].receivedData);
          respData[i].order(ByteOrder.LITTLE_ENDIAN);
        } else {
          throw new CanLibException(ErrorCode.ERR_DEVICE, ErrorDetail.COMMUNICATION_TIMEOUT);
        }
      }
    }

    return respData;
  }

  private void SendCommand(byte[] requestData) {
    debugLog(" Req", requestData);
    usbHandle.send(requestData);
  }

  private byte getNextTransId(int channelIndex) {
    byte transId;
    synchronized (nextTransId) {
      transId = nextTransId[channelIndex];
      nextTransId[channelIndex]++;
      if (nextTransId[channelIndex] == 0) {
        nextTransId[channelIndex] = 1;
      }
    }

    return transId;
  }

  private void updateTimestampAdjustment(int channelIndex, long bitrate) {
    this.timestampAdjustment[channelIndex] = ((this.numberOfBitsFromAckToValidMsg * 16000) / 1000000) / bitrate / this.hiresTimerFq;
    this.bitrate[channelIndex] = bitrate;
  }

  private long translateTimestamp(short[] timestamps, int channelIndex, byte timeOffset) {
    long time = ((((long)timestamps[2]) & 0xFFFF) << 32) |
                ((((long)timestamps[1]) & 0xFFFF) << 16) |
                ((long)timestamps[0] & 0xFFFF);

    // returned timestamp is in 10 us resolution.
    time = time / (10 * this.hiresTimerFq);

    if (this.timeoffsetValid && channelIndex >= 0) {
      time += this.timestampAdjustment[channelIndex];

      if (this.bitrate[channelIndex] >= 100000) {
        time -= (timeOffset * 100000) / this.bitrate[channelIndex];
      }
    }

    return time;
  }

  private class WaitNode {

    final byte responseCmd;
    final short transId;
    final boolean ignoreTransId;
    boolean responseReceived = false;
    byte[] receivedData;

    WaitNode(byte responseCmd, short transId, boolean ignoreTransId) {
      this.responseCmd = responseCmd;
      this.transId = transId;
      this.ignoreTransId = ignoreTransId;
    }
  }

  private void debugLog(String description, byte[] data) {
    if (debugInLogcat) {
      String msg = description + ": ";
      for (byte dataByte : data) {
        msg = msg.concat((((int) dataByte) & 0xFF) + ", ");
      }
      Log.v("CanLibDebug", msg);
    }
  }

  private void debugLog(String description) {
    if (debugInLogcat) {
      Log.v("CanLibDebug", description);
    }
  }

  private void GetAutoTxInfo(byte channel) throws CanLibException {
    AutoTxBufferReq
        request = new AutoTxBufferReq(AutoTxBufferReq.AUTOTXBUFFER_GET_INFO, channel, 0, (byte)0);

    byte[] respCmds = {AutoTxBufferResp.RespId};
    ByteBuffer[] buffer = SendCommandAndWaitResponse(request.data, respCmds, true);

    AutoTxBufferResp response = new AutoTxBufferResp(buffer[0]);

    if (response.responseType == AutoTxBufferReq.AUTOTXBUFFER_GET_INFO) {
      autoTxBufferCount[channel] = response.bufferCount;
      autoTxBufferResolution[channel] = response.timerResolution;
    }
  }

  private void GetCardInfo() throws CanLibException {
    CardInfoReq cardInfoReq = new CardInfoReq();
    byte[] respCmds     = {CardInfoResp.RespId};
    ByteBuffer[] buffer = SendCommandAndWaitResponse(cardInfoReq.data, respCmds);

    CardInfoResp response = new CardInfoResp(buffer[0]);

    this.serialNumber      = response.serialNumber;
    this.manufacturingDate = response.mfgDate;
    this.hwRevision        = response.hwRevision;
    this.hwType            = response.hwType;
    this.channelCount      = response.nChannels;
    this.ean.setEan(response.ean);
  }

  private void GetSoftwareDetails() throws CanLibException {
    SoftInfoReq req = new SoftInfoReq();
    byte[] respCmds = {SoftInfoResp.RespId};
    ByteBuffer[] buffer = SendCommandAndWaitResponse(req.data, respCmds);

    SoftInfoResp response = new SoftInfoResp(buffer[0]);
    firmwareVersionMajor = (response.fw_version >> 24) & 0xFF;
    firmwareVersionMinor = (response.fw_version >> 16) & 0xFF;
    firmwareVersionBuild = (response.fw_version & 0xFFFF);

    int swOptions = response.sw_options;
    if ((swOptions & SoftInfoResp.SWOPTION_BAD_MOOD) != 0) {
      cardRefuseToUseCan = true;
    }

    if ((swOptions & SoftInfoResp.SWOPTION_BETA) != 0) {
      cardFirmwareBeta = true;
    }

    if ((swOptions & SoftInfoResp.SWOPTION_RC) != 0) {
      cardFirmwareRc = true;
    }

    if ((swOptions & SoftInfoResp.SWOPTION_AUTO_TX_BUFFER) != 0) {
      cardAutoTxObjectBuffers= true;
    }

    if ((swOptions & SoftInfoResp.SWOPTION_CPU_FQ_MASK) == SoftInfoResp.SWOPTION_16_MHZ_CLK) {
      hiresTimerFq = 16;
    } else if ((swOptions & SoftInfoResp.SWOPTION_CPU_FQ_MASK) == SoftInfoResp.SWOPTION_24_MHZ_CLK) {
      hiresTimerFq = 24;
    } else if ((swOptions & SoftInfoResp.SWOPTION_CPU_FQ_MASK) == SoftInfoResp.SWOPTION_32_MHZ_CLK) {
      hiresTimerFq = 32;
    } else {
      hiresTimerFq = 1;
    }

    timeoffsetValid = (swOptions & SoftInfoResp.SWOPTION_TIMEOFFSET_VALID) != 0;
  }

  // **************************************************************************
  // Message classes. Placed inside KCanl since this reduce the copying.
  // Also, these classes shall never be used by anyone but KCanl.
  // **************************************************************************
  private class AutoTxBufferReq {

    static private final byte MsgLen = 12;
    static private final byte ReqId  = 72;

    private final byte[] data = new byte[AutoTxBufferReq.MsgLen];

    static private final byte AUTOTXBUFFER_GET_INFO          = 0x01;
    static private final byte AUTOTXBUFFER_CLEAR_ALL         = 0x02;
    static private final byte AUTOTXBUFFER_ACTIVATE          = 0x03;
    static private final byte AUTOTXBUFFER_DEACTIVATE        = 0x04;
    static private final byte AUTOTXBUFFER_SET_INTERVAL      = 0x05;
    static private final byte AUTOTXBUFFER_GENERAL_BURST     = 0x06;
    static private final byte AUTOTXBUFFER_SET_MESSAGE_COUNT = 0x07;

    AutoTxBufferReq(byte autoTxCmd, byte channel, int interval, byte buffNo) {

      byte msgByteCount = 1;

      data[msgByteCount++] = AutoTxBufferReq.ReqId;
      data[msgByteCount++] = autoTxCmd;
      data[msgByteCount++] = channel;

      data[msgByteCount++] = (byte)(interval);
      data[msgByteCount++] = (byte)(interval >>> 8);
      data[msgByteCount++] = (byte)(interval >>> 16);
      data[msgByteCount++] = (byte)(interval >>> 24);

      data[msgByteCount++] = buffNo;
      data[msgByteCount++] = 0; //Padding
      data[msgByteCount++] = 0;
      data[msgByteCount++] = 0;

      data[0] = msgByteCount;

      if (msgByteCount != AutoTxBufferReq.MsgLen) {
        throw new AssertionError("Message byte count differs from expected length. Expected: " + MsgLen + " Actual: " + msgByteCount);
      }
    }
  }

  private class AutoTxBufferResp {

    static private final byte MsgLen = 12;
    static private final byte RespId = 73;

    private final byte  responseType;
    private final byte  bufferCount;
    private final int   timerResolution;
    private final short capabilities;

    AutoTxBufferResp(ByteBuffer buffer) throws CanLibException {
      buffer.rewind();
      buffer.order(ByteOrder.LITTLE_ENDIAN);

      byte recMsgLen = buffer.get();
      byte recRespId = buffer.get();

      // We don't get her unless the response id is ok so it's no point in checking that
      if (recMsgLen != AutoTxBufferReq.MsgLen) {
        throw new CanLibException(CanLibException.ErrorCode.ERR_INTERNAL
            , "Received an unexpected message: " + AutoTxBufferResp.MsgLen + " " + recRespId);
      }

      this.responseType    = buffer.get();
      this.bufferCount     = buffer.get();
      this.timerResolution = buffer.getInt();
      this.capabilities    = buffer.getShort();
    }
  }

  private class CanErrorEvent {

    static private final byte MsgLen = 16;
    static private final byte RespId = 51;

    private final byte    tId;
    private final byte    flags;
    private final long    time;
    private final byte    channel;
    // uint16_t padding;
    private final byte    txErrorCounter;
    private final byte    rxErrorCounter;
    private final byte    busStatus;
    private final byte    errorFactor;

    CanErrorEvent(ByteBuffer buffer) throws CanLibException {
      buffer.rewind();
      buffer.order(ByteOrder.LITTLE_ENDIAN);

      byte recMsgLen = buffer.get();
      byte recRespId = buffer.get();

      if (recMsgLen != CanErrorEvent.MsgLen || recRespId != CanErrorEvent.RespId) {
        throw new CanLibException(CanLibException.ErrorCode.ERR_INTERNAL
            , "Received an unexpected message: " + CanErrorEvent.MsgLen + " " + recRespId);
      }

      this.tId   = buffer.get();
      this.flags = buffer.get();

      short[] recTime = new short[3];
      recTime[0] = buffer.getShort();
      recTime[1] = buffer.getShort();
      recTime[2] = buffer.getShort();
      time = translateTimestamp(recTime, -1, (byte)0);

      this.channel        = buffer.get();
      this.txErrorCounter = buffer.get();
      this.rxErrorCounter = buffer.get();
      this.busStatus      = buffer.get();
      this.errorFactor    = buffer.get();
    }
  }

  private class CardInfoReq {

    static private final byte MsgLen = 4;
    static private final byte ReqId  = 34;
    static private final byte tId    = 0;

    private final byte[] data = new byte[CardInfoReq.MsgLen];

    CardInfoReq() {

      byte msgByteCount = 1;

      data[msgByteCount++] = CardInfoReq.ReqId;
      data[msgByteCount++] = CardInfoReq.tId;
      data[msgByteCount++] = 0;

      data[0] = msgByteCount;

      if(msgByteCount != CardInfoReq.MsgLen)
      {
        throw new AssertionError("Message byte count differs from expected length. Expected: "
                                 + CardInfoReq.MsgLen + " Actual: " + msgByteCount);
      }
    }
  }

  private class CardInfoResp {

    static private final byte MsgLen = 32;
    static private final byte RespId = 35;

    private final byte   tId;
    private final byte   nChannels;
    private final int    serialNumber;
    // padding0 : 4 byte
    private final int    clockResolution;
    private final int    mfgDate;
    private final byte[] ean = new byte[8];
    private final byte   hwRevision;
    private final byte   usbHsMode;
    private final byte   hwType;

    CardInfoResp(ByteBuffer buffer) throws CanLibException {
      buffer.rewind();

      byte recMsgLen     = buffer.get();
      byte recRespId     = buffer.get();

      // We don't get her unless the response & transfer ids are ok so don't check them
      if (recMsgLen != CardInfoResp.MsgLen) {
        throw new CanLibException(CanLibException.ErrorCode.ERR_INTERNAL
            , "Received an unexpected message: " + MsgLen + " " + recRespId);
      }

      this.tId          = buffer.get();
      this.nChannels    = buffer.get();
      this.serialNumber = buffer.getInt();

      buffer.position(buffer.position() + 4); // Skip padding0

      this.clockResolution = buffer.getInt();
      this.mfgDate         = buffer.getInt();

      // The EAN code is "little endian" but this is handled in ean.java:bcdToEanString
      // so just get the bytes
      buffer.get(ean);

      this.hwRevision = buffer.get();
      this.usbHsMode  = buffer.get();
      this.hwType     = buffer.get();
    }
  }

  private class ChipStateResp {

    static private final byte MsgLen = 16;
    static private final byte RespId = 20;

    private final ChipState chipState = new ChipState();

    private final static byte BUS_STATUS_BUS_RESET_MASK   = 0x01;
    private final static byte BUS_STATUS_BUS_ERROR_MASK   = 0x10;
    private final static byte BUS_STATUS_BUS_PASSIVE_MASK = 0x20;
    private final static byte BUS_STATUS_BUS_OFF_MASK     = 0x40;

    ChipStateResp(ByteBuffer buffer) throws  CanLibException {
      buffer.rewind();

      byte recMsgLen = buffer.get();
      byte recRespId = buffer.get();

      if (recMsgLen != ChipStateResp.MsgLen) {
        throw new CanLibException(CanLibException.ErrorCode.ERR_INTERNAL
            , "Received an unexpected message: " + ChipStateResp.MsgLen + " " + recRespId);
      }

      buffer.get(); //tId
      this.chipState.channel = buffer.get();

      short[] recTime = new short[3];
      recTime[0] = buffer.getShort();
      recTime[1] = buffer.getShort();
      recTime[2] = buffer.getShort();
      this.chipState.time = translateTimestamp(recTime, this.chipState.channel, (byte)0);

      this.chipState.txErrorCounter = buffer.get();
      this.chipState.rxErrorCounter = buffer.get();

      byte reqBusStatus = buffer.get();

      switch (reqBusStatus & (BUS_STATUS_BUS_PASSIVE_MASK | BUS_STATUS_BUS_OFF_MASK)) {
        case 0:
          this.chipState.busStatus = EnumSet.of(ChipState.BusStatus.ERROR_ACTIVE);
          break;

        case BUS_STATUS_BUS_PASSIVE_MASK:
          this.chipState.busStatus =
            EnumSet.of(ChipState.BusStatus.ERROR_PASSIVE, ChipState.BusStatus.ERROR_WARNING);
          break;

        case BUS_STATUS_BUS_OFF_MASK:
          this.chipState.busStatus = EnumSet.of(ChipState.BusStatus.BUSOFF);
          break;

        case (BUS_STATUS_BUS_PASSIVE_MASK | BUS_STATUS_BUS_OFF_MASK):
          this.chipState.busStatus =
            EnumSet.of(ChipState.BusStatus.ERROR_PASSIVE, ChipState.BusStatus.ERROR_WARNING,
                       ChipState.BusStatus.BUSOFF);
          break;

        default:
          this.chipState.busStatus = EnumSet.of(ChipState.BusStatus.BUSOFF);
          break;
      }

      // Reset is treated like bus-off
      if ((reqBusStatus & BUS_STATUS_BUS_RESET_MASK) != 0) {
        this.chipState.busStatus = EnumSet.of(ChipState.BusStatus.BUSOFF);
        this.chipState.rxErrorCounter = 0;
        this.chipState.txErrorCounter = 0;
      }
    }
  }

  private class ErrorEvent {

    static private final byte MsgLen = 16;
    static private final byte RespId = 45;

    static private final byte FIRMWARE_ERR_OK              = (byte)0; // No error.
    static private final byte FIRMWARE_ERR_CAN             = (byte)1; // CAN error, addInfo1 contains error code.
    static private final byte FIRMWARE_ERR_NVRAM_ERROR     = (byte)2; // Flash error
    static private final byte FIRMWARE_ERR_NOPRIV          = (byte)3; // No privilege for attempted operation
    static private final byte FIRMWARE_ERR_ILLEGAL_ADDRESS = (byte)4; // Illegal RAM/ROM address specified
    static private final byte FIRMWARE_ERR_UNKNOWN_CMD     = (byte)5; // Unknown command or subcommand
    static private final byte FIRMWARE_ERR_FATAL           = (byte)6; // A severe error. addInfo1 contains error code.
    static private final byte FIRMWARE_ERR_CHECKSUM_ERROR  = (byte)7; // Downloaded code checksum mismatch
    static private final byte FIRMWARE_ERR_QUEUE_LEVEL     = (byte)8; // Tx queue levels (probably driver error)
    static private final byte FIRMWARE_ERR_PARAMETER       = (byte)9; // Parameter error, addInfo1 contains offending command

    private final byte    tId;
    private final byte    errorCode;
    private final long    time;
    // uint16_t padding;
    private final short   addInfo1;
    private final short   addInfo2;

    ErrorEvent(ByteBuffer buffer) throws CanLibException {
      buffer.rewind();
      buffer.order(ByteOrder.LITTLE_ENDIAN);

      byte recMsgLen = buffer.get();
      byte recRespId = buffer.get();

      // We don't get her unless the response & transfer ids are ok so don't check them
      if (recMsgLen != ErrorEvent.MsgLen) {
        throw new CanLibException(CanLibException.ErrorCode.ERR_INTERNAL
            , "Received an unexpected message: " + ErrorEvent.MsgLen + " " + recRespId);
      }

      this.tId       = buffer.get();
      this.errorCode = buffer.get();

      short[] recTime = new short[3];
      recTime[0] = buffer.getShort();
      recTime[1] = buffer.getShort();
      recTime[2] = buffer.getShort();
      this.time = translateTimestamp(recTime, -1, (byte)0);

      this.addInfo1 = buffer.getShort();
      this.addInfo2 = buffer.getShort();
    }
  }

  private class GetBusParamsReq {

    static private final byte MsgLen = 4;
    static private final byte ReqId  = 17;

    private final byte tId;
    private final byte[] data = new byte[GetBusParamsReq.MsgLen];

    GetBusParamsReq(byte channel) {

      this.tId = getNextTransId(channel);

      byte msgByteCount = 1;

      data[msgByteCount++] = GetBusParamsReq.ReqId;
      data[msgByteCount++] = this.tId;
      data[msgByteCount++] = channel;

      data[0] = msgByteCount;

      if(msgByteCount != GetBusParamsReq.MsgLen)
      {
        throw new AssertionError("Message byte count differs from expected length. Expected: "
                                 + GetBusParamsReq.MsgLen + " Actual: " + msgByteCount);
      }
    }
  }

  private class GetBusParamsResp {

    static private final byte MsgLen = 12;
    static private final byte RespId = 18;

    private final byte tId;
    private final byte channel;
    private final int  bitrate;
    private final byte tseg1;
    private final byte tseg2;
    private final byte sjw;
    private final byte no_samp;
    private final CanBusParams busParams = new CanBusParams();

    GetBusParamsResp(ByteBuffer buffer) throws CanLibException {
      buffer.rewind();

      byte recMsgLen = buffer.get();
      byte recRespId = buffer.get();

      // We don't get her unless the response & transfer ids are ok so don't check them
      if (recMsgLen != GetBusParamsResp.MsgLen) {
        throw new CanLibException(CanLibException.ErrorCode.ERR_INTERNAL
            , "Received an unexpected message: " + GetBusParamsResp.MsgLen + " " + recRespId);
      }

      this.tId     = buffer.get();
      this.channel = buffer.get();
      this.bitrate = buffer.getInt();
      this.tseg1   = buffer.get();
      this.tseg2   = buffer.get();
      this.sjw     = buffer.get();
      this.no_samp = buffer.get();
      fillCanBusParams();
    }

    private void fillCanBusParams() {
      busParams.bitRate = this.bitrate;
      busParams.tseg1   = this.tseg1;
      busParams.tseg2   = this.tseg2;
      busParams.sjw     = this.sjw;
    }
  }

  private class GetDriverModeReq {

    static private final byte MsgLen = 4;
    static private final byte ReqId  = 22;

    private final byte tId;

    private final byte[] data = new byte[GetDriverModeReq.MsgLen];

    GetDriverModeReq(byte channel) {

      this.tId = getNextTransId(channel);

      byte msgByteCount = 1;

      data[msgByteCount++] = GetDriverModeReq.ReqId;
      data[msgByteCount++] = this.tId;
      data[msgByteCount++] = channel;

      data[0] = msgByteCount;

      if(msgByteCount != GetDriverModeReq.MsgLen)
      {
        throw new AssertionError("Message byte count differs from expected length. Expected: "
                                 + GetDriverModeReq.MsgLen + " Actual: " + msgByteCount);
      }
    }
  }

  private class GetDriverModeResp {

    static private final byte MsgLen = 8;
    static private final byte RespId = 23;

    private final CanDriverType ctrlMode;
    // padding1 : 3 byte

    GetDriverModeResp(ByteBuffer buffer) throws CanLibException {
      buffer.rewind();

      byte recMsgLen     = buffer.get();
      byte recRespId     = buffer.get();
      byte recTransferId = buffer.get();

      // We don't get her unless the response & transfer ids are ok so don't check them
      if (recMsgLen != GetDriverModeResp.MsgLen) {
        throw new CanLibException(CanLibException.ErrorCode.ERR_INTERNAL
            , "Received an unexpected message: " + GetDriverModeResp.MsgLen + " " + recRespId + " " + recTransferId);
      }

      buffer.get(); //channel never used

      switch(buffer.get())
      {
        case 1:
          this.ctrlMode = CanDriverType.NORMAL;
          break;
        case 2:
          this.ctrlMode = CanDriverType.SILENT;
          break;
        default:
          throw new CanLibException(CanLibException.ErrorCode.ERR_DEVICE,
                                    CanLibException.ErrorDetail.UNSUPPORTED_DRIVER_MODE);
      }
    }
  }

  private class LEDActionReq {

    static private final byte MsgLen = 8;
    static private final byte ReqId  = 101;
    static private final byte tId    = 0;

    private final byte[] data = new byte[LEDActionReq.MsgLen];

    // action = 0 -> All LEDs on
    // action = 1 -> All LEDs off
    // action = 2 -> LED 0 on
    // action = 3 -> LED 0 off
    // and so on
    // timeout -> Milliseconds before returning to normal function
    LEDActionReq(byte action, short timeout) {

      byte msgByteCount = 1;

      data[msgByteCount++] = LEDActionReq.ReqId;
      data[msgByteCount++] = LEDActionReq.tId;
      data[msgByteCount++] = action;
      data[msgByteCount++] = (byte)(timeout);
      data[msgByteCount++] = (byte)(timeout >>> 8);
      // Padding
      data[msgByteCount++] = 0;
      data[msgByteCount++] = 0;

      data[0] = msgByteCount;

      if(msgByteCount != LEDActionReq.MsgLen)
      {
        throw new AssertionError("Message byte count differs from expected length. Expected: "
                                 + LEDActionReq.MsgLen + " Actual: " + msgByteCount);
      }
    }
  }

  private class LEDActionResp {

    static private final byte MsgLen = 4;
    static private final byte RespId = 102;

    private final byte tId;
    private final byte ack;

    LEDActionResp(ByteBuffer buffer) throws CanLibException {
      buffer.rewind();

      byte recMsgLen = buffer.get();
      byte recRespId = buffer.get();

      // We don't get her unless the response & transfer ids are ok so don't check them
      if (recMsgLen != LEDActionResp.MsgLen) {
        throw new CanLibException(CanLibException.ErrorCode.ERR_INTERNAL
            , "Received an unexpected message: " + MsgLen + " " + recRespId);
      }

      this.tId = buffer.get();
      this.ack = buffer.get();
    }
  }

  private class ResetChipReq {

    static private final byte MsgLen = 4;
    static private final byte ReqId  = 24;

    private final byte tId;

    private final byte[] data = new byte[ResetChipReq.MsgLen];

    ResetChipReq(byte channel) {

      this.tId = getNextTransId(channel);

      byte msgByteCount = 1;

      data[msgByteCount++] = ResetChipReq.ReqId;
      data[msgByteCount++] = this.tId;
      data[msgByteCount++] = channel;

      data[0] = msgByteCount;

      if(msgByteCount != ResetChipReq.MsgLen)
      {
        throw new AssertionError("Message byte count differs from expected length. Expected: "
                                 + ResetChipReq.MsgLen + " Actual: " + msgByteCount);
      }
    }
  }

  private class RxLogMessage {

    static private final byte MsgLen = 24;
    static private final byte RespId = 106;

    private final byte       channel;
    private final CanMessage canMsg = new CanMessage();

    RxLogMessage(ByteBuffer buffer) throws CanLibException {
      buffer.rewind();

      byte recMsgLen = buffer.get();
      byte respId    = buffer.get();

      // We don't get her unless the response & transfer ids are ok so don't check them
      if (recMsgLen != RxLogMessage.MsgLen) {
        throw new CanLibException(CanLibException.ErrorCode.ERR_INTERNAL
            , "Received an unexpected message: " + RxLogMessage.MsgLen + " " + respId);
      }

      this.channel = buffer.get();

      this.canMsg.setFlagsUsingBitField(buffer.get());

      short[] recTime = new short[3];
      recTime[0] = buffer.getShort();
      recTime[1] = buffer.getShort();
      recTime[2] = buffer.getShort();

      this.canMsg.dlc = buffer.get();

      byte timeOffset = buffer.get();

      this.canMsg.time = translateTimestamp(recTime, this.channel, timeOffset);

      int id = buffer.getInt();

      if ((id & 0x80000000) != 0) {
        this.canMsg.id = 0x1FFFFFFF & id;
        this.canMsg.setFlag(CanMessage.MessageFlags.EXTENDED_ID);
      }
      else {
        this.canMsg.id = 0x7FF & id;
        this.canMsg.setFlag(CanMessage.MessageFlags.STANDARD_ID);
      }

      buffer.get(this.canMsg.data);

      this.canMsg.direction = CanMessage.Direction.RX;
    }
  }

  private class RxMessage {

    static private final byte MsgLen    = 24;
    static private final byte RespIdStd = 12;
    static private final byte RespIdExt = 14;

    static private final byte MSGFLAG_ERROR_FRAME  = (byte)0x01; // Msg is a bus error
    static private final byte MSGFLAG_OVERRUN      = (byte)0x02; // Msgs following this has been lost
    static private final byte MSGFLAG_NERR         = (byte)0x04; // NERR active during this msg
    static private final byte MSGFLAG_WAKEUP       = (byte)0x08; // Msg rcv'd in wakeup mode
    static private final byte MSGFLAG_REMOTE_FRAME = (byte)0x10; // Msg is a remote frame
    static private final byte MSGFLAG_RESERVED_1   = (byte)0x20; // Reserved for future usage
    static private final byte MSGFLAG_TX           = (byte)0x40; // TX acknowledge
    static private final byte MSGFLAG_TXRQ         = (byte)0x80; // TX request

    private final byte channel;

    private final CanMessage canMsg = new CanMessage();

    RxMessage(ByteBuffer buffer) throws CanLibException {

      buffer.rewind();
      buffer.order(ByteOrder.LITTLE_ENDIAN);

      byte recMsgLen = buffer.get();
      byte recRespId = buffer.get();

      if (recMsgLen != RxMessage.MsgLen) {
        throw new CanLibException(CanLibException.ErrorCode.ERR_INTERNAL
            , "Received an unexpected message: " + MsgLen + " " + recRespId);
      }

      this.channel = buffer.get();

      byte recFlags = buffer.get();
      this.canMsg.flags.clear();

      if ((recFlags & RxMessage.MSGFLAG_ERROR_FRAME) != 0) canMsg.flags.add(CanMessage.MessageFlags.ERROR_FRAME);
      if ((recFlags & RxMessage.MSGFLAG_OVERRUN) != 0) canMsg.flags.add(CanMessage.MessageFlags.ERR_HW_OVERRUN);
      if ((recFlags & RxMessage.MSGFLAG_REMOTE_FRAME) != 0) canMsg.flags.add(CanMessage.MessageFlags.REMOTE_REQUEST);
      if ((recFlags & RxMessage.MSGFLAG_TX) != 0) canMsg.flags.add(CanMessage.MessageFlags.TX_ACK);
      if ((recFlags & RxMessage.MSGFLAG_TXRQ) != 0) canMsg.flags.add(CanMessage.MessageFlags.TX_RQ);

      short[] recTime = new short[3];
      recTime[0] = buffer.getShort();
      recTime[1] = buffer.getShort();
      recTime[2] = buffer.getShort();
      this.canMsg.time = translateTimestamp(recTime, this.channel, (byte)0);

      int id = buffer.get() & 0x1F;
      id = id << 6;
      id += buffer.get() & 0x3F;
      if (recRespId == RxMessage.RespIdExt) {
        id = id << 4;
        id += buffer.get() & 0x0F;
        id = id << 8;
        id += buffer.get() & 0xFF;
        id = id << 6;
        id += buffer.get() & 0x3F;
        this.canMsg.flags.add(CanMessage.MessageFlags.EXTENDED_ID);
      }
      else {
        //Skip 3 bytes
        buffer.getShort();
        buffer.get();
        this.canMsg.flags.add(CanMessage.MessageFlags.STANDARD_ID);
      }
      this.canMsg.id = id;

      this.canMsg.dlc = buffer.get() & 0x0F;

      this.canMsg.data[0] = buffer.get();
      this.canMsg.data[1] = buffer.get();
      this.canMsg.data[2] = buffer.get();
      this.canMsg.data[3] = buffer.get();
      this.canMsg.data[4] = buffer.get();
      this.canMsg.data[5] = buffer.get();
      this.canMsg.data[6] = buffer.get();
      this.canMsg.data[7] = buffer.get();

      this.canMsg.direction = CanMessage.Direction.RX;
    }
  }

  private class SetDriverModeReq {

    static private final byte MsgLen = 8;
    static private final byte ReqId  = 21;
    static private final byte tId    = 0;

    private final byte[] data = new byte[SetDriverModeReq.MsgLen];


    SetDriverModeReq(byte channel, CanDriverType driverType) throws CanLibException {

      byte msgByteCount = 1;

      data[msgByteCount++] = SetDriverModeReq.ReqId;
      data[msgByteCount++] = SetDriverModeReq.tId;
      data[msgByteCount++] = channel;

      switch (driverType)
      {
        case NORMAL:
          data[msgByteCount++] = 1;
          break;
        case SILENT:
          data[msgByteCount++] = 2;
          break;
        case OFF:
        case SELF_RECEPTION:
        default:
          throw new CanLibException(CanLibException.ErrorCode.ERR_PARAM,
                                    CanLibException.ErrorDetail.UNSUPPORTED_DRIVER_MODE,
                                    driverType.toString());
      }

      // Padding
      data[msgByteCount++] = 0;
      data[msgByteCount++] = 0;
      data[msgByteCount++] = 0;

      data[0] = msgByteCount;

      if(msgByteCount != SetDriverModeReq.MsgLen)
      {
        throw new AssertionError("Message byte count differs from expected length. Expected: "
                                 + SetDriverModeReq.MsgLen + " Actual: " + msgByteCount);
      }
    }
  }

  private class SetBusParamsReq {

    static private final byte MsgLen = 12;
    static private final byte ReqId  = 16;

    private final byte tId;
    private final byte[] data = new byte[SetBusParamsReq.MsgLen];

    SetBusParamsReq(byte channel, CanBusParams busParams) {

       this.tId = getNextTransId(channel);

      byte msgByteCount = 1;

      data[msgByteCount++] = SetBusParamsReq.ReqId;
      data[msgByteCount++] = this.tId;
      data[msgByteCount++] = channel;
      // bitrate shall be little endian
      data[msgByteCount++] = (byte)(busParams.bitRate);
      data[msgByteCount++] = (byte)(busParams.bitRate >>> 8);
      data[msgByteCount++] = (byte)(busParams.bitRate >>> 16);
      data[msgByteCount++] = (byte)(busParams.bitRate >>> 24);
      data[msgByteCount++] = (byte)busParams.tseg1;
      data[msgByteCount++] = (byte)busParams.tseg2;
      data[msgByteCount++] = (byte)busParams.sjw;
      data[msgByteCount++] = (byte)CanBusParams.numSamplingPoints;

      data[0] = msgByteCount;

      if(msgByteCount != SetBusParamsReq.MsgLen)
      {
        throw new AssertionError("Message byte count differs from expected length. Expected: "
                                 + SetBusParamsReq.MsgLen + " Actual: " + msgByteCount);
      }
    }
  }

  private class SoftInfoReq {

    static private final byte MsgLen = 4;
    static private final byte ReqId  = 38;
    static private final byte tId    = 0;

    private final byte[] data = new byte[SoftInfoReq.MsgLen];

    SoftInfoReq() {

      byte msgByteCount = 1;

      data[msgByteCount++] = SoftInfoReq.ReqId;
      data[msgByteCount++] = SoftInfoReq.tId;
      data[msgByteCount++] = 0;

      data[0] = msgByteCount;

      if(msgByteCount != SoftInfoReq.MsgLen)
      {
        throw new AssertionError("Message byte count differs from expected length. Expected: "
                                 + SoftInfoReq.MsgLen + " Actual: " + msgByteCount);
      }
    }
  }

  private class SoftInfoResp {

    static private final byte MsgLen = 32;
    static private final byte RespId = 39;
    static private final byte tId    = 0;

    // padding0 : 1 byte
    private final int   sw_options;
    private final int   fw_version;
    private final short max_outstanding_tx;
    // padding1 : 18 byte

    private final static int SWOPTION_CONFIG_MODE      = 0x01; // Memorator in config mode.
    private final static int SWOPTION_AUTO_TX_BUFFER   = 0x02; // Firmware has auto tx buffers
    private final static int SWOPTION_BETA             = 0x04; // Firmware is a beta release
    private final static int SWOPTION_RC               = 0x08; // Firmware is a release candidate
    private final static int SWOPTION_BAD_MOOD         = 0x10; // Firmware detected config error or the like
    private final static int SWOPTION_CPU_FQ_MASK      = 0x60;
    private final static int SWOPTION_16_MHZ_CLK       = 0x00; // hires timers run at 16 MHZ
    private final static int SWOPTION_32_MHZ_CLK       = 0x20; // hires timers run at 32 MHZ
    private final static int SWOPTION_24_MHZ_CLK       = 0x40; // hires timers run at 24 MHZ
    private final static int SWOPTION_TIMEOFFSET_VALID = 0x80; // the timeOffset field in txAcks and logMessages is valid

    SoftInfoResp(ByteBuffer buffer) throws CanLibException {
      buffer.rewind();

      byte recMsgLen     = buffer.get();
      byte recRespId     = buffer.get();
      byte recTransferId = buffer.get();

      // We don't get her unless the response & transfer ids are ok so don't check them
      if (recMsgLen != SoftInfoResp.MsgLen) {
        throw new CanLibException(CanLibException.ErrorCode.ERR_INTERNAL
            , "Received an unexpected message: " + SoftInfoResp.MsgLen + " " + recRespId + " " + recTransferId);
      }

      buffer.position(buffer.position() + 1); // Skip padding0

      this.sw_options         = buffer.getInt();
      this.fw_version         = buffer.getInt();
      this.max_outstanding_tx = buffer.getShort();
    }
  }

  private class StartChipReq {

    static private final byte MsgLen = 4;
    static private final byte ReqId  = 26;

    private final byte tId;

    private final byte[] data = new byte[StartChipReq.MsgLen];

    StartChipReq(byte channel) {

      this.tId = getNextTransId(channel);

      byte msgByteCount = 1;

      data[msgByteCount++] = StartChipReq.ReqId;
      data[msgByteCount++] = this.tId;
      data[msgByteCount++] = channel;

      data[0] = msgByteCount;

      if(msgByteCount != StartChipReq.MsgLen)
      {
        throw new AssertionError("Message byte count differs from expected length. Expected: "
                                 + StartChipReq.MsgLen + " Actual: " + msgByteCount);
      }
    }
  }

  private class StartChipResp {

    static private final byte MsgLen = 4;
    static private final byte RespId = 27;

    private final byte tId;
    private final byte ack;

    StartChipResp(ByteBuffer buffer) throws CanLibException {
      buffer.rewind();

      byte recMsgLen = buffer.get();
      byte recRespId = buffer.get();

      // We don't get her unless the response & transfer ids are ok so don't check them
      if (recMsgLen != StartChipResp.MsgLen) {
        throw new CanLibException(CanLibException.ErrorCode.ERR_INTERNAL
            , "Received an unexpected message: " + StartChipResp.MsgLen + " " + recRespId);
      }

      this.tId = buffer.get();
      this.ack = buffer.get();
    }
  }

  private class StopChipReq {

    static private final byte MsgLen = 4;
    static private final byte ReqId  = 28;

    private final byte tId;

    private final byte[] data = new byte[StopChipReq.MsgLen];

    StopChipReq(byte channel) {

      this.tId = getNextTransId(channel);

      byte msgByteCount = 1;

      data[msgByteCount++] = StopChipReq.ReqId;
      data[msgByteCount++] = this.tId;
      data[msgByteCount++] = channel;

      data[0] = msgByteCount;

      if(msgByteCount != StopChipReq.MsgLen)
      {
        throw new AssertionError("Message byte count differs from expected length. Expected: "
                                 + StopChipReq.MsgLen + " Actual: " + msgByteCount);
      }
    }
  }

  private class StopChipResp {

    static private final byte MsgLen = 4;
    static private final byte RespId = 29;

    private final byte tId;
    private final byte ack;

    StopChipResp(ByteBuffer buffer) throws CanLibException {
      buffer.rewind();

      byte recMsgLen = buffer.get();
      byte recRespId = buffer.get();

      // We don't get her unless the response & transfer ids are ok so don't check them
      if (recMsgLen != StopChipResp.MsgLen) {
        throw new CanLibException(CanLibException.ErrorCode.ERR_INTERNAL
            , "Received an unexpected message: " + MsgLen + " " + recRespId);
      }

      this.tId = buffer.get();
      this.ack = buffer.get();
    }
  }

  private class TxAcknowledge {

    static private final byte MsgLen = 12;
    static private final byte RespId = 50;

    private final byte    channel;
    private final byte    tId;
    private final long    time;
    private final byte    flags;
    private final byte    timeOffset;

    TxAcknowledge(ByteBuffer buffer) throws CanLibException {
      buffer.rewind();
      buffer.order(ByteOrder.LITTLE_ENDIAN);

      byte recMsgLen = buffer.get();
      byte recRespId = buffer.get();

      if (recMsgLen != TxAcknowledge.MsgLen) {
        throw new CanLibException(CanLibException.ErrorCode.ERR_INTERNAL
            , "Received an unexpected message: " + MsgLen + " " + recRespId);
      }

      this.channel = buffer.get();
      this.tId     = buffer.get();

      short[] recTime = new short[3];
      recTime[0] = buffer.getShort();
      recTime[1] = buffer.getShort();
      recTime[2] = buffer.getShort();

      this.flags      = buffer.get();
      this.timeOffset = buffer.get();

      this.time = translateTimestamp(recTime, this.channel, this.timeOffset);
    }
  }

  private class TxReq {

    static private final byte MsgLen = 20;

    private final byte ReqId;
    private final byte tId;

    private final byte[] data = new byte[TxReq.MsgLen];

    TxReq(byte channel, CanMessage msg) throws CanLibException {

      if (msg.flags.contains(CanMessage.MessageFlags.EXTENDED_ID)) {
        this.ReqId = 15;
        if (msg.id < 0 || msg.id > 0x1FFFFFFF) {
          this.tId = 0;
          throw new CanLibException(CanLibException.ErrorCode.ERR_PARAM,
                                    CanLibException.ErrorDetail.ILLEGAL_ID,
                                    "Valid range for extended message id is 0 - 0x1FFFFFFF");
        }
      }
      else if (msg.flags.contains(CanMessage.MessageFlags.STANDARD_ID)) {
        this.ReqId = 13;
        if (msg.id < 0 || msg.id > 0x7FF) {
          this.tId = 0;
          throw new CanLibException(CanLibException.ErrorCode.ERR_PARAM,
                                    CanLibException.ErrorDetail.ILLEGAL_ID,
                                    "Valid range for standard message id is 0 - 0x7FF");
        }
      }
      else {
        this.ReqId = 0;
        this.tId = 0;
        throw new CanLibException(CanLibException.ErrorCode.ERR_PARAM,
                                  CanLibException.ErrorDetail.NOT_SUPPORTED,
                                  "Message has to be of either extended or standard type");
      }

      this.tId = getNextTransId(channel);

      byte msgByteCount = 1;

      data[msgByteCount++] = this.ReqId;
      data[msgByteCount++] = channel;
      data[msgByteCount++] = tId;

      if (msg.flags.contains(CanMessage.MessageFlags.EXTENDED_ID)) {
        data[msgByteCount++] = (byte)((0x1F & (msg.id >>> 24) | 0x80));
        data[msgByteCount++] = (byte)(0x3F & (msg.id >>> 18));
        data[msgByteCount++] = (byte)(0x0F & (msg.id >>> 14));
        data[msgByteCount++] = (byte)(0xFF & (msg.id >>> 6));
        data[msgByteCount++] = (byte)(0x3F & msg.id);
      }
      else {
        data[msgByteCount++] = (byte)(0x1F & (msg.id >>> 6));
        data[msgByteCount++] = (byte)(0x3F & msg.id);
        data[msgByteCount++] = 0;
        data[msgByteCount++] = 0;
        data[msgByteCount++] = 0;
      }

      data[msgByteCount++] = (byte)(0x0F & msg.dlc);

      for (byte i = 0; i < 8; i++)
      {
        data[msgByteCount++] = msg.data[i];
      }

      data[msgByteCount++] = 0;     // padding
      data[msgByteCount++] = (byte)msg.getFlagsAsMask();

      data[0] = msgByteCount;

      if(msgByteCount != TxReq.MsgLen)
      {
        throw new AssertionError("Message byte count differs from expected length. Expected: "
                                 + TxReq.MsgLen + " Actual: " + msgByteCount);
      }
    }
  }

  private class TxRequest {
    // This message is in fact something that we receive even though it's
    // called request in filo_cmds.h. Maybe call it TxRequestEvent?

    static private final byte MsgLen = 8;
    static private final byte RespId = 60;

    private final byte    channel;
    private final byte    tId;
    private final long    time;
    // uint16_t padding;

    TxRequest(ByteBuffer buffer) throws CanLibException {
      buffer.rewind();
      buffer.order(ByteOrder.LITTLE_ENDIAN);

      byte recMsgLen = buffer.get();
      byte recRespId = buffer.get();

      // We don't get her unless the response & transfer ids are ok so don't check them
      if (recMsgLen != TxRequest.MsgLen) {
        throw new CanLibException(CanLibException.ErrorCode.ERR_INTERNAL
            , "Received an unexpected message: " + TxRequest.MsgLen + " " + recRespId);
      }

      this.channel = buffer.get();
      this.tId     = buffer.get();

      short[] recTime = new short[3];
      recTime[0] = buffer.getShort();
      recTime[1] = buffer.getShort();
      recTime[2] = buffer.getShort();
      this.time = translateTimestamp(recTime, this.channel, (byte)0);
    }
  }
}
