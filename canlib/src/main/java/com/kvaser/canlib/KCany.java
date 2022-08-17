package com.kvaser.canlib;

import android.os.*;
import android.support.annotation.Size;
import android.util.*;

import java.nio.*;
import java.util.*;
import java.text.DateFormat;

import com.kvaser.canlib.CanLibException.ErrorDetail;
import com.kvaser.canlib.CanLibException.ErrorCode;

/**
 * Device driver for KCany interfaces. Uses UsbDeviceHandle methods to communicate with device.
 */
class KCany implements KvDeviceInterface {

  final static int KCANY_CMD_SIZE = 32;

  private final static boolean debugInLogcat = false;

  private final static int HYDRA_MAX_OUTSTANDING_TX = 200;
  private final static int MAX_NUM_CHANNELS = 16;

  private final static byte BROADCAST = 0x0f;
  private final static byte BROADCAST_DEBUG = 0x1f;
  private final static byte BROADCAST_ERROR = 0x2f;
  private final static byte ROUTER_HE = 0x00;
  private final static byte DYNAMIC_HE = ROUTER_HE;
  private final static byte ILLEGAL_HE = 0x3e;

  private final KvDevices deviceType;
  private final List<WaitNode> waitList = new ArrayList<>();
  List<CanChannelEventListener> canChannelListeners = new ArrayList<>();
  private long serialNumber;
  private String pcbId = "";
  private UsbDeviceHandle usbHandle;
  private int maxPacketSizeIn;
  private int maxOutstandingTx;
  private int autoTxBufferCount;
  private int autoTxBufferResolution;
  private int firmwareVersionMajor;
  private int firmwareVersionMinor;
  private int firmwareVersionBuild;
  private Ean ean = new Ean();
  private long clockResolution;
  private int hiresTimerFq = 1;
  private long manufacturingDate;
  private byte hwRevision;
  private byte usbHsMode;
  private byte hwType;
  private byte canTimeStampRef;
  private byte channelCount;
  private boolean cardFirmwareBeta = false;          // Firmware is beta
  private boolean cardFirmwareRc = false;            // Firmware is release candidate
  private boolean cardAutoRespObjectBuffers = false; // Firmware supports auto-resp object buffers
  private boolean cardRefuseToRun = false;           // Major problem detected
  private boolean cardRefuseToUseCan = false;        // Major problem detected
  private boolean cardAutoTxObjectBuffers = false;   // Firmware supports periodic tx object buffers

  private short[] nextTransId = new short[MAX_NUM_CHANNELS];
  private ChannelHeList channelHeList = new ChannelHeList(MAX_NUM_CHANNELS);

  KCany(UsbDeviceHandle usbHandle, int maxPacketSizeIn,
        KvDevices deviceType) throws CanLibException {
    this.usbHandle = usbHandle;
    this.maxPacketSizeIn = maxPacketSizeIn;
    this.deviceType = deviceType;
    usbHandle.addListener(this);

    Arrays.fill(nextTransId, (byte) 1);

    /* Map channels */
    CmdMapChannel cmdMC = new CmdMapChannel(this);
    cmdMC.req.destination = ROUTER_HE;
    for (byte i = 0; i < MAX_NUM_CHANNELS; i++) {
      cmdMC.req.transId = (short) (0x40 | i);
      cmdMC.req.name = "CAN".getBytes();
      cmdMC.req.channel = i;
      cmdMC.sendAndWaitResponse();

      if (cmdMC.resp.transId > 0x007f || cmdMC.resp.transId < 0x0040) {
        // The transaction ID is not as expected, we do not know what channel to map, device misbehaving.
        throw new CanLibException(ErrorCode.ERR_DEVICE, ErrorDetail.INIT_ERROR,
                                  "CMD_MAP_CHANNEL_RESP, invalid transId: " + cmdMC.resp.transId);
      }

      switch (cmdMC.resp.transId & 0x0ff0) {
        case 0x40:
          int channel = (int) cmdMC.resp.transId & 0x000f;
          channelHeList.addPair(channel, cmdMC.resp.heAddress);
          break;

        default:
          // Ignore
          break;
      }
    }

    /* Get card info */
    CmdGetCardInfo cmdCI = new CmdGetCardInfo(this);
    if (cmdCI.resp.oemUnlockCode != 0) {
      // This device requires an unlock code to function correctly. This is not supported.
      throw new CanLibException(ErrorCode.ERR_DEVICE, ErrorDetail.DEVICE_LOCKED);
    }
    cmdCI.req.destination = ILLEGAL_HE;
    cmdCI.req.transId = 0x50;
    cmdCI.req.dataLevel = 0;
    cmdCI.sendAndWaitResponse();
    serialNumber = cmdCI.resp.serialNumber;
    clockResolution = cmdCI.resp.clockResolution;
    manufacturingDate = cmdCI.resp.manufacturingDate;
    ean.setEan(cmdCI.resp.ean);
    hwRevision = cmdCI.resp.hwRevision;
    usbHsMode = cmdCI.resp.usbHsMode;
    hwType = cmdCI.resp.hwType;
    canTimeStampRef = cmdCI.resp.canTimeStampRef;
    channelCount = cmdCI.resp.channelCount;
    pcbId = cmdCI.resp.pcbId;

    /* Get software info */
    CmdGetSoftwareInfo cmdSI = new CmdGetSoftwareInfo(this);
    cmdSI.req.destination = ILLEGAL_HE;
    cmdSI.req.transId = 0x51;
    cmdSI.sendAndWaitResponse();
    maxOutstandingTx = cmdSI.resp.maxOutstandingTx;

    if (maxOutstandingTx > HYDRA_MAX_OUTSTANDING_TX) {
      maxOutstandingTx = HYDRA_MAX_OUTSTANDING_TX;
    }
    maxOutstandingTx--;   // Can't use all elements!

    /* Get software details */
    CmdGetSoftwareDetails cmdSD = new CmdGetSoftwareDetails(this);
    cmdSD.req.destination = ILLEGAL_HE;
    cmdSD.req.transId = 0x52;
    cmdSD.sendAndWaitResponse();
    firmwareVersionMajor = (cmdSD.resp.swVersion >> 24) & 0xff;
    firmwareVersionMinor = (cmdSD.resp.swVersion >> 16) & 0xff;
    firmwareVersionBuild = (cmdSD.resp.swVersion & 0xffff);

    if ((cmdSD.resp.swOptions & CmdGetSoftwareDetails.SWOPTION_BAD_MOOD) != 0) {
      cardRefuseToUseCan = true;
    }

    if ((cmdSD.resp.swOptions & CmdGetSoftwareDetails.SWOPTION_BETA) != 0) {
      cardFirmwareBeta = true;
    }

    if ((cmdSD.resp.swOptions & CmdGetSoftwareDetails.SWOPTION_RC) != 0) {
      cardFirmwareRc = true;
    }

    if ((cmdSD.resp.swOptions & CmdGetSoftwareDetails.SWOPTION_AUTO_TX_BUFFER) != 0) {
      cardAutoTxObjectBuffers = true;
    }

    if ((cmdSD.resp.swOptions & CmdGetSoftwareDetails.SWOPTION_CPU_FQ_MASK)
        == CmdGetSoftwareDetails.SWOPTION_80_MHZ_CLK) {
      hiresTimerFq = 80;
    } else if ((cmdSD.resp.swOptions & CmdGetSoftwareDetails.SWOPTION_CPU_FQ_MASK)
               == CmdGetSoftwareDetails.SWOPTION_24_MHZ_CLK) {
      hiresTimerFq = 24;
    } else {
      hiresTimerFq = 1;
    }

    /* Auto Tx Buffer */
    if (cardAutoTxObjectBuffers) {
      CmdAutoTxBuffer cmdATB = new CmdAutoTxBuffer(this);
      cmdATB.req.destination = (byte) (channelHeList.channelToHe(0) & 0x7f);
      cmdATB.req.transId = 0x53;
      cmdATB.req.requestType = CmdAutoTxBuffer.AUTOTXBUFFER_CMD_GET_INFO;
      cmdATB.sendAndWaitResponse();
      if (cmdATB.resp.responseType == CmdAutoTxBuffer.AUTOTXBUFFER_CMD_GET_INFO) {
        autoTxBufferCount = cmdATB.resp.bufferCount;
        autoTxBufferResolution = cmdATB.resp.timerResolution;
      }
    }
  }

  public void close() {
    // Remove all access information as the device closes
    CanChannelAccess.releaseAccessPerDevice("" + (int) serialNumber, ean.getEanString(),
                                            channelCount);
    if(usbHandle != null) {
      usbHandle.close();
    }
  }

  public int getNumberOfChannels() {
    return channelCount;
  }

  public boolean isVirtual() {
    return false;
  }

  public void setBusParams(int channelIndex, CanBusParams busParams) throws CanLibException {
    CmdSetBusParams cmdBP = new CmdSetBusParams(this);
    cmdBP.req.destination = (byte) (channelHeList.channelToHe(channelIndex) & 0x7f);
    cmdBP.req.transId = getNextTransId(channelIndex);
    cmdBP.req.busParams = busParams;
    cmdBP.req.channel = (byte) channelIndex;
    cmdBP.sendAndWaitResponse();
  }

  public CanBusParams getBusParams(int channelIndex) throws CanLibException {
    CmdGetBusParams cmdBP = new CmdGetBusParams(this);
    cmdBP.req.destination = (byte) (channelHeList.channelToHe(channelIndex) & 0x7f);
    cmdBP.req.transId = getNextTransId(channelIndex);
    cmdBP.req.paramType = 0; // Do not use CAN FD
    cmdBP.sendAndWaitResponse();

    return cmdBP.resp.busParams;
  }

  public void busOn(int channelIndex) throws CanLibException {
    CmdBusOn cmdBO = new CmdBusOn(this);
    cmdBO.req.destination = (byte) (channelHeList.channelToHe(channelIndex) & 0x7f);
    cmdBO.req.transId = getNextTransId(channelIndex);
    cmdBO.sendAndWaitResponse();
  }

  public void busOff(int channelIndex) throws CanLibException {
    CmdBusOff cmdBO = new CmdBusOff(this);
    cmdBO.req.destination = (byte) (channelHeList.channelToHe(channelIndex) & 0x7f);
    cmdBO.req.transId = getNextTransId(channelIndex);
    cmdBO.sendAndWaitResponse();
  }

  public void setBusOutputControl(int channelIndex,
                                  CanDriverType driverType) throws CanLibException {
    CmdSetDriverMode cmdDM = new CmdSetDriverMode(this);
    cmdDM.req.destination = (byte) (channelHeList.channelToHe(channelIndex) & 0x7f);
    cmdDM.req.transId = getNextTransId(channelIndex);
    switch (driverType) {
      case NORMAL:
        cmdDM.req.driverMode = 0x01;
        break;
      case SILENT:
        cmdDM.req.driverMode = 0x02;
        break;
      case OFF:
      case SELF_RECEPTION:
      default:
        throw new CanLibException(ErrorCode.ERR_PARAM, ErrorDetail.UNSUPPORTED_DRIVER_MODE,
                                  driverType.toString());
    }
    cmdDM.send();
  }

  public CanDriverType getBusOutputControl(int channelIndex) throws CanLibException {
    // First firmware version that is known to respond correctly to Get Driver Mode Request is 3.0.546
    if ((firmwareVersionMajor < 3) || (firmwareVersionMajor == 3) && (firmwareVersionMinor == 0)
                                      && (firmwareVersionBuild < 546)) {
      throw new CanLibException(ErrorCode.ERR_PARAM, ErrorDetail.NOT_SUPPORTED);
    }

    CanDriverType retVal;
    CmdGetDriverMode cmdDM = new CmdGetDriverMode(this);
    cmdDM.req.destination = (byte) (channelHeList.channelToHe(channelIndex) & 0x7f);
    cmdDM.req.transId = getNextTransId(channelIndex);
    cmdDM.req.channel = (byte) channelIndex;
    cmdDM.sendAndWaitResponse();
    switch (cmdDM.resp.driverMode) {
      case 0x01:
        retVal = CanDriverType.NORMAL;
        break;
      case 0x02:
        retVal = CanDriverType.SILENT;
        break;
      default:
        throw new CanLibException(ErrorCode.ERR_DEVICE, ErrorDetail.UNSUPPORTED_DRIVER_MODE);
    }

    return retVal;
  }

  public void write(int channelIndex, CanMessage msg) throws CanLibException {

    if ((msg.isFlagSet(CanMessage.MessageFlags.EXTENDED_ID) && ((msg.id & 0x7FFFFFFF) >= (1 << 29)))
        || (msg.isFlagSet(CanMessage.MessageFlags.STANDARD_ID) && (msg.id >= (1 << 11)))) {
      // id out of range
      throw new CanLibException(ErrorCode.ERR_PARAM, ErrorDetail.ILLEGAL_ID);
    }
    if (msg.isFlagSet(CanMessage.MessageFlags.EXTENDED_ID)) {
      msg.id |= 0x80000000;
    }
    CmdTxCanMessage cmdTxCanMessage = new CmdTxCanMessage(this);
    cmdTxCanMessage.req.destination = (byte) (channelHeList.channelToHe(channelIndex) & 0x7f);
    cmdTxCanMessage.req.transId = getNextTransId(channelIndex);
    cmdTxCanMessage.req.id = msg.id;
    cmdTxCanMessage.req.data = msg.data.clone();
    cmdTxCanMessage.req.channel = (byte) channelIndex;
    cmdTxCanMessage.req.dlc = (byte) msg.dlc;
    cmdTxCanMessage.req.flags = (byte) (msg.getFlagsAsMask() & 0xff);
    cmdTxCanMessage.send();
  }

  /**
   * Generates string representations of all device info.
   *
   * @return Bundle with pairs of key (String) - value (String), that represents the device.
   */
  public Bundle getDeviceInfo() {
    Bundle b = new Bundle();
    b.putString("Device Name", deviceType.getDeviceName());
    b.putString("Hardware Type", Byte.toString(hwType));
    b.putString("Manufacturer", "Kvaser AB");
    b.putString("Card EAN", ean.getEanString());
    b.putString("Product Number", ean.getProductNumber());
    b.putString("Serial Number", Long.toString(serialNumber));
    b.putString("Firmware Version",
                firmwareVersionMajor + "." + firmwareVersionMinor + "." + firmwareVersionBuild);
    b.putString("Hardware Revision", Byte.toString(hwRevision));
    DateFormat df = DateFormat.getDateInstance();
    df.setTimeZone(TimeZone.getTimeZone("UTC"));
    b.putString("Mfg Date", df.format(new Date(manufacturingDate * 1000)));
    b.putString("PCB Revision", pcbId);
    return b;
  }

  public Ean getEan() {
    return ean;
  }

  public int getSerialNumber() {
    return (int) serialNumber;
  }

  public void flashLeds() {
    FlashThread flashThread = new FlashThread(this, 5, (short) 300, (short) 300);
    flashThread.start();
  }

  public void registerCanChannelEventListener(CanChannelEventListener listener) {
    canChannelListeners.add(listener);
  }

  public void unregisterCanChannelEventListener(CanChannelEventListener listener) {
    canChannelListeners.remove(listener);
  }

  public void UsbDataReceived(byte[] data) {
    int count = 0;
    byte cmdNum;

    while (count < data.length) {
      if (data.length < (count + KCANY_CMD_SIZE)) {
        break;
      }
      cmdNum = data[count];
      if (cmdNum == 0) {
        // Fast-forward count to the next maxPacketSizeIn boundary
        count = ((count + maxPacketSizeIn) / maxPacketSizeIn) * maxPacketSizeIn;
      } else {
        HandleUsbCommand(Arrays.copyOfRange(data, count, count + KCANY_CMD_SIZE));
        count += KCANY_CMD_SIZE;
      }
    }
  }

  private void HandleUsbCommand(@Size(KCANY_CMD_SIZE - 1) byte[] data) {
    if (data.length != KCANY_CMD_SIZE) {
      // Should not happen since this function shall only be called by UsbDataReceived that makes
      // sure only the correct length is used. However, in case it would happen anyway we will not
      // be able to pare it - so just return.
      return;
    }
    debugLog("Resp", data);

    ByteBuffer resp = ByteBuffer.wrap(data);
    resp.order(ByteOrder.LITTLE_ENDIAN);
    byte cmd = resp.get();
    byte cmdIOP = resp.get();
    short cmdIOPSeq = resp.getShort();
    short transId = (short) (cmdIOPSeq & 0x0fff);
    byte sourceHE =
        (byte) ((((cmdIOP & 0xC0) >>> 2) & 0x30) | (((cmdIOPSeq & 0xf000) >>> 12) & 0x0f));

    // Handle all commands that are sent on event by the device here (i.e. the ones that are not
    // responses to requests. This is where events to listeners shall be generated.
    switch (cmd) {
      case CmdTxCanMessage.CMD_TX_ACKNOWLEDGE:
        CmdTxCanMessage cmdTxAck = new CmdTxCanMessage(this);
        cmdTxAck.parseResponse(resp);
        CanMessage txMsg = new CanMessage();
        txMsg.setFlagsUsingBitField(cmdTxAck.resp.flags);
        if ((cmdTxAck.resp.id & 0x80000000) == 0) {
          /* Standard ID */
          txMsg.setFlag(CanMessage.MessageFlags.STANDARD_ID);
          txMsg.id = (cmdTxAck.resp.id & 0x7FF);
        } else {
          /* Extended ID */
          txMsg.setFlag(CanMessage.MessageFlags.EXTENDED_ID);
          txMsg.id = (cmdTxAck.resp.id & 0x1FFFFFFF);
        }
        txMsg.dlc = cmdTxAck.resp.dlc;
        txMsg.time = ticksToTimestamp(cmdTxAck.resp.time);
        txMsg.data = cmdTxAck.resp.data.clone();
        txMsg.direction = CanMessage.Direction.TX;
        int txChannel = channelHeList.heToChannel(sourceHE);
        for (CanChannelEventListener listener : canChannelListeners) {
          if (listener.getChannelIndex() == txChannel) {
            listener.canChannelEvent(CanChannelEventListener.CanChannelEventType.MESSAGE, txMsg);
          }
        }
        break;

      case CmdLogMessage.CMD_LOG_MESSAGE:
        CmdLogMessage cmdLog = new CmdLogMessage(this);
        cmdLog.parseResponse(resp);
        CanMessage rxMsg = new CanMessage();
        rxMsg.setFlagsUsingBitField(cmdLog.resp.flags);
        if ((cmdLog.resp.id & 0x80000000) == 0) {
          /* Standard ID */
          rxMsg.setFlag(CanMessage.MessageFlags.STANDARD_ID);
          rxMsg.id = (cmdLog.resp.id & 0x7FF);
        } else {
          /* Extended ID */
          rxMsg.setFlag(CanMessage.MessageFlags.EXTENDED_ID);
          rxMsg.id = (cmdLog.resp.id & 0x1FFFFFFF);
        }
        rxMsg.dlc = cmdLog.resp.dlc;
        rxMsg.time = ticksToTimestamp(cmdLog.resp.time);
        rxMsg.data = cmdLog.resp.data.clone();
        rxMsg.direction = CanMessage.Direction.RX;
        int rxChannel = channelHeList.heToChannel(sourceHE);
        for (CanChannelEventListener listener : canChannelListeners) {
          if (listener.getChannelIndex() == rxChannel) {
            listener.canChannelEvent(CanChannelEventListener.CanChannelEventType.MESSAGE, rxMsg);
          }
        }
        break;

      case CmdChipState.CMD_CHIP_STATE_EVENT:
        CmdChipState cmdCS = new CmdChipState(this);
        cmdCS.parseResponse(resp);
        ChipState chipState = new ChipState();
        chipState.time = ticksToTimestamp(cmdCS.resp.time);
        chipState.channel = cmdCS.resp.channel;
        chipState.rxErrorCounter = (int) cmdCS.resp.rxErrorCounter & 0xFF;
        chipState.txErrorCounter = (int) cmdCS.resp.txErrorCounter & 0xFF;
        switch (cmdCS.resp.busStatus & (CmdChipState.BUS_STATUS_BUS_PASSIVE_MASK
                                        | CmdChipState.BUS_STATUS_BUS_OFF_MASK)) {
          case 0:
            chipState.busStatus = EnumSet.of(ChipState.BusStatus.ERROR_ACTIVE);
            break;

          case CmdChipState.BUS_STATUS_BUS_PASSIVE_MASK:
            chipState.busStatus =
                EnumSet.of(ChipState.BusStatus.ERROR_PASSIVE, ChipState.BusStatus.ERROR_WARNING);
            break;

          case CmdChipState.BUS_STATUS_BUS_OFF_MASK:
            chipState.busStatus = EnumSet.of(ChipState.BusStatus.BUSOFF);
            break;

          case (CmdChipState.BUS_STATUS_BUS_PASSIVE_MASK | CmdChipState.BUS_STATUS_BUS_OFF_MASK):
            chipState.busStatus = EnumSet
                .of(ChipState.BusStatus.ERROR_PASSIVE, ChipState.BusStatus.ERROR_WARNING,
                    ChipState.BusStatus.BUSOFF);
            break;

          default:
            break;
        }

        // Reset is treated like bus-off
        if ((cmdCS.resp.busStatus & CmdChipState.BUS_STATUS_BUS_RESET_MASK) != 0) {
          chipState.busStatus = EnumSet.of(ChipState.BusStatus.BUSOFF);
          chipState.rxErrorCounter = 0;
          chipState.txErrorCounter = 0;
        }

        for (CanChannelEventListener listener : canChannelListeners) {
          if (listener.getChannelIndex() == chipState.channel) {
            listener
                .canChannelEvent(CanChannelEventListener.CanChannelEventType.CHIP_STATE, chipState);
          }
        }
        break;

      case 45:
        debugLog(
            "CMD_ERROR_EVENT received. TODO: Implement handler."); //TODO Timeouts are currently used for error handling.
        break;

      case 51:
        debugLog(
            "CMD_CAN_ERROR_EVENT received. TODO: Implement handler."); //TODO Timeouts are currently used for error handling.
        break;

      default:
        break;
    }

    // Notify all waiters of this response
    synchronized (waitList) {
      for (int i = 0; i < waitList.size(); i++) {
        synchronized (waitList.get(i)) {
          if ((waitList.get(i).responseCmd == cmd) && (waitList.get(i).transId == transId)) {
            waitList.get(i).responseReceived = true;
            waitList.get(i).receivedData = Arrays.copyOf(data, waitList.get(i).receivedData.length);
            waitList.get(i).notifyAll();
          }
        }
      }
    }
  }

  private long ticksToTimestamp(int[] ticks) {
    long temp;
    int divisor = 10 * hiresTimerFq;
    int[] resTime = new int[3];
    resTime[0] = ticks[2] / divisor;
    temp = (ticks[2] % divisor) << 16;
    resTime[1] = (int) ((temp + ticks[1]) / divisor);
    temp = ((temp + ticks[1]) % divisor) << 16;
    resTime[2] = (int) ((temp + ticks[0]) / divisor);
    return (long) ((resTime[1] << 16) + resTime[2]);
  }

  private short getNextTransId(int channelIndex) {
    short transId = nextTransId[channelIndex];
    nextTransId[channelIndex]++;
    nextTransId[channelIndex] = (short) (nextTransId[channelIndex] & 0x0fff);
    if (nextTransId[channelIndex] == 0) {
      nextTransId[channelIndex] = 1;
    }
    return transId;
  }

  /**
   * Sends a command to the device over USB and blocks while waiting for the response(s). In case
   * of no response there is a timeout of 2 seconds.
   *
   * @param requestData An array with the command bytes to send
   * @param responseCmd An array of command IDs in the expected response(s)
   * @param transId     The transaction ID in the expected response(s)
   * @return array of ByteBuffers with the received response(s), or null in case of error or timeout
   */
  ByteBuffer[] SendCommandAndWaitResponse(byte[] requestData, byte[] responseCmd,
                                          short transId) throws CanLibException {
    WaitNode[] waitNodes = new WaitNode[responseCmd.length];
    for (int i = 0; i < waitNodes.length; i++) {
      waitNodes[i] = new WaitNode(responseCmd[i], transId);
      synchronized (waitList) {
        waitList.add(waitNodes[i]);
      }
    }
    ByteBuffer[] respData = new ByteBuffer[responseCmd.length];
    debugLog(" Req", requestData);
    for (int i = 0; i < waitNodes.length; i++) {
      synchronized (waitNodes[i]) {
        if (i == 0) {
          // Send shall only be done once, but it has to be within the "synchronized" block
          usbHandle.send(requestData);
        }
        int timeoutCounter = 0;
        while ((!waitNodes[i].responseReceived) && (timeoutCounter < 20)) {
          try {
            waitNodes[i].wait(100);
          } catch (InterruptedException e) {
            throw new CanLibException(ErrorCode.ERR_INTERNAL, ErrorDetail.INTERRUPTED_THREAD,
                                      "Thread was interrupted while waiting for response from device.");
          }
          timeoutCounter++;
        }
        if (timeoutCounter == 20) {
          debugLog(
              "Timeout occurred while waiting for response cmd " + (((int) waitNodes[i].responseCmd)
                                                                    & 0xff) + " (transId "
              + waitNodes[i].transId + ")");
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

  void SendCommand(@Size(32) byte[] requestData) {
    debugLog(" Req", requestData);
    usbHandle.send(requestData);
  }

  private void debugLog(String description, byte[] data) {
    if (debugInLogcat) {
      String msg = description + ": ";
      for (byte dataByte : data) {
        msg += (((int) dataByte) & 0xff) + ", ";
      }
      Log.v("CanLibDebug", msg);
    }
  }

  private void debugLog(String description) {
    if (debugInLogcat) {
      Log.v("CanLibDebug", description);
    }
  }

  private class WaitNode {

    byte responseCmd;
    short transId;
    boolean responseReceived = false;
    byte[] receivedData = new byte[KCANY_CMD_SIZE];

    WaitNode(byte responseCmd, short transId) {
      this.responseCmd = responseCmd;
      this.transId = transId;
    }
  }

  private class ChannelHeList {

    private List<Integer> channel2he;

    public ChannelHeList(int maxNumChannels) {
      channel2he = new ArrayList<>(maxNumChannels);
      for (int i = 0; i < maxNumChannels; i++) {
        channel2he.add(ILLEGAL_HE & 0xff);
      }
    }

    public int channelToHe(int channel) {
      if (channel < channel2he.size()) {
        return channel2he.get(channel);
      } else {
        return ILLEGAL_HE;
      }
    }

    public int heToChannel(int he) {
      int channel = channel2he.indexOf(he);
      if (channel < 0) {
        return 0xff;
      } else {
        return channel;
      }
    }

    public void addPair(int channel, int he) {
      if (channel < channel2he.size()) {
        channel2he.set(channel, he);
      }
    }

  }

  private class FlashThread extends Thread {

    private int numFlash = 0;
    private short onTime = 0;
    private short offTime = 0;
    private KCany driver;

    public FlashThread(KCany driver, int numFlash, short onTime, short offTime) {
      this.numFlash = numFlash;
      this.onTime = onTime;
      this.offTime = offTime;
      this.driver = driver;
    }

    @Override
    public void run() {
      CmdLedAction cmdLA = new CmdLedAction(driver);
      try {
        for (int i = 0; i < numFlash - 1; i++) {
          cmdLA.req.subCommand = CmdLedAction.LED_SUBCOMMAND_ALL_LEDS_ON;
          cmdLA.req.timeout = (short) (onTime + 10);
          cmdLA.sendAndWaitResponse();
          Thread.sleep(onTime);

          cmdLA.req.subCommand = CmdLedAction.LED_SUBCOMMAND_ALL_LEDS_OFF;
          cmdLA.req.timeout = (short) (offTime + 10);
          cmdLA.sendAndWaitResponse();
          Thread.sleep(offTime);
        }
        cmdLA.req.subCommand = CmdLedAction.LED_SUBCOMMAND_ALL_LEDS_ON;
        cmdLA.req.timeout = onTime;
        cmdLA.sendAndWaitResponse();

      } catch (InterruptedException | CanLibException e) {
        // InterruptedException: May occur in Thread.sleep
        // CanLibException:      Probably a communication timeout when talking to the device
        // --> just ignore this and exit the flash thread
        e.printStackTrace();
      }
    }
  }

}
