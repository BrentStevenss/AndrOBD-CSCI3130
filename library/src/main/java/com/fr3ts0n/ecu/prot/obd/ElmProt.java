/*
 * (C) Copyright 2015 by fr3ts0n <erwin.scheuch-heilig@gmx.at>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston,
 * MA 02111-1307 USA
 */

package com.fr3ts0n.ecu.prot.obd;

import static com.fr3ts0n.ecu.prot.obd.AdaptiveTiming.ELM_TIMEOUT_DEFAULT;

import com.fr3ts0n.ecu.CMD;
import com.fr3ts0n.ecu.PROT;
import com.fr3ts0n.ecu.RSP_ID;
import com.fr3ts0n.ecu.STAT;
import com.fr3ts0n.prot.TelegramListener;
import com.fr3ts0n.prot.TelegramWriter;

import java.beans.PropertyChangeEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;


/**
 * Communication protocol to talk to a ELM327 OBD interface
 *
 * @author erwin
 */
public class ElmProt
	extends ObdProt
	implements TelegramListener, TelegramWriter, Runnable
{
	/**
	 * virtual OBD service for CAN monitoring
	 */
	public static final int OBD_SVC_CAN_MONITOR = 256;
	
	/**
	 * property name for ECU addresses
	 */
	public static final String PROP_ECU_ADDRESS = "ecuaddr";
	/**
	 * property name for protocol status
	 */
	public static final String PROP_STATUS = "status";
	
	/**
	 * CAN protocol handler
	 */
	public static final CanProtFord canProt = new CanProtFord();
	/**
	 * Adaptive timing handler
	 */
	public final AdaptiveTiming mAdaptiveTiming = new AdaptiveTiming();

	/**
	 * Command sender
	 */
	public final CommandSender mCommandSender = new CommandSender();
	
	/**
	 * number of bytes expected from opponent
	 */
	private int charsExpected = 0;

	
	/**
	 * preferred ELM protocol to be selected
	 */
	static private PROT preferredProtocol = PROT.ELM_PROT_AUTO;
	
	/**
	 * list of identified ECU addresses
	 */
	private final TreeSet<Integer> ecuAddresses = new TreeSet<Integer>();
	/**
	 * selected ECU address
	 */
	private int selectedEcuAddress = 0;
	/**
	 * custom ELM initialisation commands
	 */
	private final Vector<String> customInitCommands = new Vector<String>();

	/**
	 * Creates a new instance of ElmProtocol
	 */
	public ElmProt()
	{
	}
	
	/**
	 * set preferred ELM protocol to be used
	 *
	 * @param protoIndex preferred ELM protocol index
	 */
	public static void setPreferredProtocol(int protoIndex)
	{
		preferredProtocol = PROT.values()[protoIndex];
		log.info("Preferred protocol: " + preferredProtocol);
	}
	
	/**
	 * set ECU address to be received
	 *
	 * @param ecuAddress ECU address to be filtered / 0 = clear address filter
	 */
	public void setEcuAddress(int ecuAddress)
	{
		log.info(String.format("Set ECU address: 0x%x", ecuAddress));
		selectedEcuAddress = ecuAddress;
		// ensure headers are off
		mCommandSender.pushCommand(CMD.SETHEADER, 0);
		// set/clear RX filter
		mCommandSender.sendCommand((selectedEcuAddress != 0) ? CMD.SETCANRXFLT : CMD.CLRCANRXFLT,
			selectedEcuAddress);
	}


	/**
	 * disable a set of ELM commands ELM commands from preference
	 *
	 * @param disabledCmds set of ELM commands (ATxx strings) to be disabled
	 */
	public static void disableCommands(Set<String> disabledCmds)
	{
		for (CMD cmd : CMD.values())
		{
			cmd.setEnabled(disabledCmds == null
			               || !disabledCmds.contains(cmd.toString()));
		}
	}
	


	public void setElmMsgTimeout(int newTimeout){
		mAdaptiveTiming.setElmTimeoutMin(newTimeout);
		// queue the new timeout message

	}

	
	/**
	 * return numeric ID to given response
	 *
	 * @param response clear text response from ELM adapter
	 */
	private static RSP_ID getResponseId(String response)
	{
		RSP_ID result = RSP_ID.UNKNOWN;
		for (RSP_ID id : RSP_ID.values())
		{
			if (response.startsWith(id.toString()))
			{
				result = id;
				break;
			}
		}
		// return ID
		return (result);
	}
	
	/**
	 * send ELM adapter to sleep mode
	 */
	public void goToSleep()
	{
		mCommandSender.sendCommand(CMD.LOWPOWER, 0);
	}
	
	/**
	 * reset ELM adapter
	 */
	public void reset()
	{
		// reset all learned protocol data
		super.reset();
		// either RESET or INFO command needs to be enabled
		if (CMD.RESET.isEnabled())
		{ mCommandSender.sendCommand(CMD.RESET, 0); }
		else
		{ mCommandSender.sendCommand(CMD.INFO, 0); }
	}
	
	/**
	 * request addresses of all connected ECUs
	 * (received IDs are evaluated in @ref:handleDataMessage)
	 */
	private void queryEcus()
	{
		// set status to ECU detection
		setStatus(STAT.ECU_DETECT);
		
		// clear all identified ECU addresses
		ecuAddresses.clear();
		// clear selected ECU
		selectedEcuAddress = 0;
		// remember to disable headers again
		mCommandSender.pushCommand(CMD.SETHEADER, 0);
		// request PIDs (from all devices)
		mCommandSender.addQueue("0100");
		// enable headers
		mCommandSender.sendCommand(CMD.SETHEADER, 1);
	}
	
	private void initialize()
	{
		// set status to INITIALIZING
		setStatus(STAT.INITIALIZING);
		
		// push custom init commands
		mCommandSender.addAll(customInitCommands);
		
		// set to preferred protocol
		mCommandSender.pushCommand(CMD.SETPROT, preferredProtocol.ordinal());
		
		// initialize adaptive timing handler
		mAdaptiveTiming.initialize();
		
		// speed up protocol by removing spaces and line feeds from output
		mCommandSender.pushCommand(CMD.SETSPACES, 0);
		mCommandSender.pushCommand(CMD.SETLINEFEED, 0);
		
		// immediate set echo off
		mCommandSender.pushCommand(CMD.ECHO, 0);
	}
	
	/**
	 * Implementation of TelegramListener
	 */
	
	/**
	 * multiline response is pending, for responses w/o a length info
	 */
	private boolean responsePending = false;

	/**
	 * handle incoming protocol telegram
	 *
	 * @param buffer - telegram buffer
	 * @return number of listeners notified
	 */
	@Override
	public synchronized int handleTelegram(char[] buffer) {
		String bufferStr = new String(buffer);
		log.fine(this.toString() + " RX:'" + bufferStr + "'");

		if (buffer.length == 0 || lastTxMsg.compareToIgnoreCase(bufferStr) == 0) {
			return 0;
		}

		log.fine("ELM rx:'" + bufferStr + "' (" + lastTxMsg + ")");

		RSP_ID responseId = getResponseId(bufferStr);

		switch (responseId) {
			case QMARK:
			case NODATA:
			case OK:
			case ERROR:
			case SEARCH:
				setStatus(status != STAT.ECU_DETECT ? STAT.CONNECTING : status);
				lastRxMsg = bufferStr;
				break;
			case STOPPED:
				lastRxMsg = bufferStr;
				mCommandSender.addQueue(String.valueOf(mCommandSender.getLastCommand()));
				break;
			case MODEL:
				initialize();
				break;
			case PROMPT:
				handlePromptResponse(bufferStr);
				break;
			default:
				handleDefaultResponse(bufferStr);
				break;
		}

		return 0; // Adjust return value as per your logic
	}


	private void handlePromptResponse(String bufferStr) {
		RSP_ID lastResponseId = getResponseId(lastRxMsg);

		switch (lastResponseId) {
			case NOCONN:
			case NOCONN2:
			case CANERROR:
			case BUSERROR:
			case BUSINIERR:
			case BUSINIERR2:
			case BUSINIERR3:
			case BUSBUSY:
			case FBERROR:
				setStatus(STAT.DISCONNECTED);
				mCommandSender.addQueue(String.valueOf(mCommandSender.getLastCommand()));
				mCommandSender.pushCommand(CMD.SETPROT, preferredProtocol.ordinal());
				mAdaptiveTiming.initialize();
				mCommandSender.sendCommand(CMD.PROTOCLOSE, 0);
				break;
			case DATAERROR:
				setStatus(STAT.DATAERROR);
				mCommandSender.sendCommand(CMD.WARMSTART, 0);
				break;
			case BUFFERFULL:
			case RXERROR:
				setStatus(status == STAT.DATAERROR ? STAT.DATAERROR : STAT.RXERROR);
				mCommandSender.sendCommand(CMD.WARMSTART, 0);
				break;
			case ERROR:
				setStatus(STAT.ERROR);
				mCommandSender.sendCommand(CMD.WARMSTART, 0);
				break;
			case NODATA:
				setStatus(STAT.NODATA);
				if (service != OBD_SVC_NONE) {
					mCommandSender.addQueue(String.valueOf(createTelegram(emptyBuffer, service, getNextSupportedPid())));
				}
				mAdaptiveTiming.adapt(true);
				mCommandSender.pushCommand(CMD.SETPROT, preferredProtocol.ordinal());
			default:
				lastRxMsg = bufferStr;
				break;
		}
	}

	private int handleDefaultResponse(String bufferStr) {
		int result = 0;
		// If we are still initializing, check for address entries
		switch (status) {
			case ECU_DETECT: {
				// Start of 0100 response is end of address
				int adrEnd = bufferStr.indexOf("41");
				// If not a service response, check for possible NRC
				if (adrEnd < 0) {
					adrEnd = bufferStr.indexOf("7F01");
				}
				int adrStart = bufferStr.lastIndexOf(".") + 1;
				if (adrEnd > adrStart) {
					int adrLen = adrEnd - adrStart;
					if ((adrLen % 2) != 0) {
						/*
						 * Odd address length
						 * -> CAN address length = 3 digits + 2 digits frame type
						 */
						adrLen = 3;
					} else {
						if (adrLen == 6) {
							/*
							 * 6 char (3 byte) prefix -> ISO9141 address <FF><RR><SS>
							 * FF - Frame type
							 * RR - Receiver address
							 * SS - Sender address
							 */
							adrLen = 2;
							adrStart = adrEnd - adrLen;
						} else if (adrLen == 10) {
							/*
							 * 29 bit CAN address
							 * <CP><AABBCC><FT>
							 * CP = CAN priority (5 relevant bits)
							 * AABBCC = Address (24 Bit)
							 * FT = Frame type
							 * -> CAN address length = 32 bits / 8 digits <CP><AABBCC>
							 */
							adrLen = 8;
							adrStart = 0;
						}
					}
					// Extract address
					String address = bufferStr.substring(adrStart, adrStart + adrLen);

					log.fine(String.format("Found ECU address: 0x%s", address));
					// Add to list of addresses
					ecuAddresses.add(Integer.valueOf(address, 16));
				}
				return lastRxMsg.length();
			}
			default:
				break;
		}

		// We are connected
		setStatus(STAT.CONNECTED);

		// ELM clone verbose message (starting with '+')
		if (bufferStr.startsWith("+")) {
			// Ignore message
			return result;
		}

		// Is this a length identifier?
		if (bufferStr.startsWith("0") && bufferStr.length() == 3) {
			// Remember the length to be expected
			charsExpected = Integer.valueOf(bufferStr, 16) * 2;
			lastRxMsg = "";
			return result;
		}

		// Check for multiline responses
		int idx = bufferStr.indexOf(':');

		// ISO multi-line response with format SVC PID MSGID DATA...
		if ((idx < 0) && (bufferStr.length() == 14)) {
			final int[] dfcServices = {OBD_SVC_READ_CODES, OBD_SVC_PENDINGCODES, OBD_SVC_PERMACODES};
			int msgService = Integer.valueOf(bufferStr.substring(0, 2), 0x10) & ~0x40;
			// If response to current service and no DFC response
			if (msgService == getService() && Arrays.binarySearch(dfcServices, msgService) < 0) {
				// Use header on 1st response, cut from continuation messages
				int msgId = Integer.valueOf(bufferStr.substring(4, 6), 0x10);
				idx = msgId <= 1 ? 0 : 5; // Index of last digit message ID
			}
		}

		if (idx >= 0) {
			if (idx == 0) {
				// Initial ISO multiline message
				lastRxMsg = bufferStr;
				charsExpected = 0;
			} else if (bufferStr.startsWith("0")) {
				// First line of a multiline message
				lastRxMsg = bufferStr.substring(idx + 1);
			} else {
				// Continuation lines, concatenate response without line counter
				lastRxMsg += bufferStr.substring(idx + 1);
			}

			// No length known, set marker for pending response
			responsePending = (charsExpected == 0);
		} else {
			// Otherwise, use this as last received message
			lastRxMsg = bufferStr;
			charsExpected = 0;
			responsePending = false;
		}

		// If we haven't received complete result yet, then wait for the rest
		if (lastRxMsg.length() < charsExpected) {
			return result;
		}

		// Trim a multiline response to expected length (cut off padding)
		if ((charsExpected > 0) && (lastRxMsg.length() > charsExpected)) {
			lastRxMsg = lastRxMsg.substring(0, charsExpected);
		}

		// If response is finished, handle it
		if (!responsePending) {
			result = handleDataMessage(lastRxMsg);
		}

		return result;
	}


	/**
	 * forward data message for further handling
	 *
	 * @param lastRxMsg received message to be forwarded
	 * @return number of bytes processed
	 */
	private int handleDataMessage(String lastRxMsg)
	{
		int result = 0;
		
		// otherwise process response
		switch (service)
		{
			case OBD_SVC_NONE:
				// ignore messages
				break;
			
			case OBD_SVC_CAN_MONITOR:
				result = canProt.handleTelegram(lastRxMsg.toCharArray());
				break;
			
			default:
				// Let the OBD protocol handle the telegram
				result = super.handleTelegram(lastRxMsg.toCharArray());
		}
		return result;
	}
	
	// switch to exit the demo thread
	public static boolean runDemo;
	
	/**
	 * run threaded loop to simulate incoming telegrams
	 */
	public void run()
	{
		int value = 0;
		Integer pid;
		runDemo = true;
		
		log.info("ELM DEMO thread started");
		while (runDemo)
		{
			try
			{
				handleTelegram(RSP_ID.MODEL.toString().toCharArray());
				// test case for issue AndrOBD/#61
				handleTelegram("+CONNECTING<<94:65:2D:9E:DF:B5".toCharArray());
				
				setStatus(STAT.ECU_DETECT);
				handleTelegram("SEARCHING...".toCharArray());
				handleTelegram("7EA074100000000".toCharArray());
				handleTelegram("486B104100BF9FA8919B".toCharArray());
				handleTelegram("...486B104100BF9FA8919B".toCharArray());
				handleTelegram("7E8064100000000".toCharArray());
				handleTelegram("7E9074100000000".toCharArray());
				handleTelegram("7EA074100000000".toCharArray());
				// test case for issue AndrOBD/#60
				handleTelegram("18DAF110064100BE3EB811".toCharArray());
				// test case for issue AndrOBD-Plugin/#10 (NRC22 on detect)
				handleTelegram("7E8037F0122".toCharArray());
				setStatus(STAT.ECU_DETECTED);
				
				while (runDemo)
				{
					switch (service)
					{
						// read any kinds of trouble codes
						case OBD_SVC_READ_CODES:
						case OBD_SVC_PENDINGCODES:
						case OBD_SVC_PERMACODES:
							// simulate 12 TCs set as multy line response
							// number of codes = 12 + MIL ON
							handleTelegram("41018C000000".toCharArray());
							// send codes as multy line response
							handleTelegram("014".toCharArray());
							handleTelegram("0:438920B920BD".toCharArray());
							handleTelegram("1:C002242A246E02".toCharArray());
							handleTelegram("2:36010101162453".toCharArray());

							// simulate 12 TCs set as subsequent single line responses
							// send codes as subsequent single line responses
							handleTelegram("478420BA20BC".toCharArray());
							handleTelegram("4784C004242B".toCharArray());

							// test pattern from AndrOBD/#78
							// 4 DFCs, 10 byte multiline response , padded
							handleTelegram("00A".toCharArray());
							handleTelegram("0:4A8401180122".toCharArray());
							handleTelegram("1:02232610000000".toCharArray());

							Thread.sleep(500);
							break;
						
						// otherwise send data ...
						case OBD_SVC_DATA:
						case OBD_SVC_FREEZEFRAME:
							pid = getNextSupportedPid();
							if (pid != 0)
							{
								value++;
								value &= 0xFF;
								// format new data message and handle it as new reception
								handleTelegram(String.format(
									service == OBD_SVC_DATA ? "4%X%02X%02X%02X%02X%02X"
									                        : "4%X%02X00%02X%02X%02X%02X",
									service, pid, value, value, value, value).toCharArray());
							}
							else
							{
								// simulate "ALL PIDs supported"
								int i;
								for (i = 0; i < 0xE0; i += 0x20)
								{
									handleTelegram(String.format(
										service == OBD_SVC_DATA ? "4%X%02XFFFFFFFF"
										                        : "4%X%02X00FFFFFFFF", service, i)
										               .toCharArray());
								}
								handleTelegram(String.format(
									service == OBD_SVC_DATA ? "4%X%02XFFFFFFFE"
									                        : "4%X%02X00FFFFFFFE", service, i)
									               .toCharArray());
							}
							break;
						
						case OBD_SVC_VEH_INFO:
							pid = getNextSupportedPid();
							if (pid == 0)
							{
								// simulate "ALL pids supported"
								handleTelegram("490054000000".toCharArray());
							}
							
							// send VIN "0123456789ABCDEFG"
							handleTelegram("014".toCharArray());
							handleTelegram("1:49020130313233".toCharArray());
							handleTelegram("2:343536373839".toCharArray());
							handleTelegram("3:41424344454647".toCharArray());
							
							// send 2 CAL-IDs "GSPA..." without length id
							handleTelegram("0:490402475350".toCharArray());
							handleTelegram("1:412D3132333435".toCharArray());
							handleTelegram("2:363738393030".toCharArray());
							handleTelegram("3:30313233".toCharArray());
							handleTelegram("4:343536373839".toCharArray());
							handleTelegram("5:414243444546".toCharArray());
							
							// CAL-ID 01234567
							handleTelegram("490601234567".toCharArray());
							break;
						
						case OBD_SVC_CTRL_MODE:
							handleTelegram("4800C0000000".toCharArray());
							break;

						case OBD_SVC_NONE:
							// just keep quiet until soneone requests something
							break;
						
						default:
							// respond "service not supported"
							handleTelegram(String.format("7F%02X11", service).toCharArray());
							Thread.sleep(500);
							break;
						
					}
					Thread.sleep(50);
				}
			}
			catch (Exception ex)
			{
				log.severe(ex.getLocalizedMessage());
			}
		}
		log.info("ELM DEMO thread finished");
	}
	
	/**
	 * set custom initialisation commands
	 *
	 * @param commands custom initialisation commands
	 */
	public void setCustomInitCommands(String[] commands)
	{
		List<String> cmds = Arrays.asList(commands);
		// reverse list, since all commands are pushed rather than queued
		Collections.reverse(cmds);
		// clear list
		customInitCommands.clear();
		// add all entries
		customInitCommands.addAll(cmds);
	}
	
	/**
	 * Setter for property service.
	 *
	 * @param service    New value of property service.
	 * @param clearLists clear data list for this service
	 */
	@Override
	public void setService(int service, boolean clearLists)
	{
		// log the change in service
		if (service != this.service)
		{
			log.info("OBD Service: " + this.service + "->" + service);
			this.service = service;
			
			// send corresponding command(s)
			switch (service)
			{
				case OBD_SVC_CAN_MONITOR:
					mCommandSender.sendCommand(CMD.CANMONITOR, 0);
					break;
				
				default:
					super.setService(service, clearLists);
			}
		}
	}
	
	/**
	 * set OBD service - compatibility function
	 *
	 * @param service New value of property service.
	 */
	public void setService(int service)
	{
		setService(service, true);
	}
	
	/**
	 * Holds value of property status.
	 */
	private STAT status = STAT.UNDEFINED;
	
	/**
	 * Getter for property status.
	 *
	 * @return Value of property status.
	 */
	public STAT getStatus()
	{
		return this.status;
	}
	
	/**
	 * Setter for property status.
	 *
	 * @param status New value of property status.
	 */
	private void setStatus(STAT status)
	{
		STAT oldStatus = this.status;
		this.status = status;
		if (status != oldStatus)
		{
			log.info("Status change: " + oldStatus + "->" + status);
			// ECUs detected -> send identified ECU addresses
			if (status == STAT.ECU_DETECTED)
			{
				firePropertyChange(
					new PropertyChangeEvent(this, PROP_ECU_ADDRESS, null, ecuAddresses));
			}
			
			// now fire regular status change
			firePropertyChange(new PropertyChangeEvent(this, PROP_STATUS, oldStatus, status));
		}
	}
}
