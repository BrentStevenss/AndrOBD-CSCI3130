package com.fr3ts0n.ecu;

import java.util.logging.Logger;

/**
 * numeric IDs for commands
 */
public enum CMD
{
    RESET("Z", 0, true), ///< reset adapter
    WARMSTART("WS", 0, true), ///< warm start
    PROTOCLOSE("PC", 0, true), ///< protocol close
    DEFAULTS("D", 0, true), ///< set all to defaults
    INFO("I", 0, true), ///< request adapter info
    LOWPOWER("LP", 0, true), ///< switch to low power mode
    ECHO("E", 1, true), ///< enable/disable echo
    SETLINEFEED("L", 1, true), ///< enable/disable line feeds
    SETSPACES("S", 1, true), ///< enable/disable spaces
    SETHEADER("H", 1, true), ///< enable/disable header response
    GETPROT("DP", 0, true), ///< get protocol
    SETPROT("SP", 1, true), ///< set protocol
    CANMONITOR("MA", 0, true), ///< monitor CAN messages
    SETPROTAUTO("SPA", 1, true), ///< set protocol auto
    ADAPTTIMING("AT", 1, true), ///< Set ELM internal adaptive timing (0-2)
    SETTIMEOUT("ST", 2, true), ///< set timeout (x*4ms)
    SETTXHDR("SH", 3, true), ///< set TX header
    SETCANRXFLT("CRA", 3, true), ///< set CAN RX filter
    CLRCANRXFLT("CRA", 0, true); ///< clear CAN RX filter

    static final String CMD_HEADER = "AT";
    private final String command;
    public final int paramDigits;
    private final boolean disablingAllowed;
    private boolean enabled = true;
    protected static Logger log = Logger.getLogger("com.fr3ts0n.prot");


    CMD(String cmd, int numDigitsParameter, @SuppressWarnings("SameParameterValue") boolean allowAdaption)
    {
        command = cmd;
        paramDigits = numDigitsParameter;
        disablingAllowed = allowAdaption;
    }

    @Override
    public String toString()
    {
        return CMD_HEADER + command;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        if (disablingAllowed)
        {
            this.enabled = enabled;
        }
        // log current state
        log.fine(String.format("ELM command '%s' -> %s",
                toString(),
                this.enabled ? "enabled" : "disabled"));
    }
    public boolean isDisablingAllowed()
    {
        return disablingAllowed;
    }
}