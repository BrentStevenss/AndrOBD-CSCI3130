package com.fr3ts0n.ecu.prot.obd;

import com.fr3ts0n.ecu.CMD;
import com.fr3ts0n.prot.TelegramSender;

import java.util.Vector;
import java.util.logging.Logger;

public class CommandSender extends TelegramSender {
    /**
     * remember last command which was sent
     */
    private char[] lastCommand;
    /** queue of ELM commands to be sent */
    static final Vector<String> cmdQueue = new Vector<String>();
    /** Logging object */
    protected static Logger log = Logger.getLogger("com.fr3ts0n.prot");

    public CommandSender(){

    }

    @Override
    public void sendTelegram(char[] buffer)
    {
        log.fine(this.toString() + " TX:'" + String.valueOf(buffer) + "'");
        lastCommand = buffer;
        super.sendTelegram(buffer);
    }
    /**
     * create ELM command string from command id and paramter
     *
     * @param cmdID ID of ELM command
     * @param param parameter for ELM command (0 if not required)
     * @return command char sequence or NULL if command disabled/invalid
     */
    private String createCommand(CMD cmdID, int param)
    {
        String cmd = null;
        if (cmdID.isEnabled())
        {
            cmd = cmdID.toString();
            // if parameter is required and provided, add parameter to command
            if (cmdID.paramDigits > 0)
            {
                String fmtString = "%0".concat(String.valueOf(cmdID.paramDigits)).concat("X");
                cmd += String.format(fmtString, param);
            }
        }
        // return command String
        return cmd;
    }
    /**
     * queue command to ELM command queue
     *
     * @param cmdID ID of ELM command
     * @param param parameter for ELM command (0 if not required)
     */
    public void pushCommand(CMD cmdID, int param)
    {
        String cmd = createCommand(cmdID, param);
        if (cmd != null) { cmdQueue.add(cmd); }
    }
    public void addQueue(String command){
        cmdQueue.add(command);
    }
    public void addAll(Vector<String> commands){
        cmdQueue.addAll(commands);
    }
    public int queueSize(){
        return cmdQueue.size();
    }
    public String getLastCommand(){
        return cmdQueue.lastElement();
    }
    public void removeCommand(String command){
        cmdQueue.remove(command);
    }
    /**
     * send command to ELM adapter
     *
     * @param cmdID ID of ELM command
     * @param param parameter for ELM command (0 if not required)
     */
    public void sendCommand(CMD cmdID, int param)
    {
        // now send command
        String cmd = createCommand(cmdID, param);
        if (cmd != null) { sendTelegram(cmd.toCharArray()); }
    }
}
