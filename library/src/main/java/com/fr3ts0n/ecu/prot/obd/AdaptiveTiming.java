package com.fr3ts0n.ecu.prot.obd;

import com.fr3ts0n.ecu.AdaptTimingMode;
import com.fr3ts0n.ecu.CMD;

import java.util.logging.Logger;

/**
 * Adaptive ELM timing handler
 * * optimizes ELM message timeout at runtime
 */
public class AdaptiveTiming
{
    public AdaptiveTiming(){

    }
    private CommandSender mCommandSender =  new CommandSender();

    /** Logging object */
    protected static Logger log = Logger.getLogger("com.fr3ts0n.prot");
    /**
     * for ELM message timeout handling
     */
    /**
     * max. ELM Message Timeout [ms]
     */
    protected static final int ELM_TIMEOUT_MAX = 1000;
    /**
     * default ELM message timeout
     */
    protected static final int ELM_TIMEOUT_DEFAULT = 200;
    /**
     * Learning resolution of ELM Message Timeout [ms]
     */
    protected static final int ELM_TIMEOUT_RES = 4;
    /**
     * minimum ELM timeout
     */
    int ELM_TIMEOUT_MIN = 12;
    /**
     * minimum ELM timeout (learned from vehicle)
     */
    int ELM_TIMEOUT_LRN_LOW = 12;
    /**
     * ELM message timeout: defaults to approx 200 [ms]
     */
   protected int elmMsgTimeout = ELM_TIMEOUT_MAX;

    /**
     * adaptive timing handling enabled?
     */
    private AdaptTimingMode mode = AdaptTimingMode.OFF;

    public AdaptTimingMode getMode()
    {
        return mode;
    }

    /**
     * min. (configured) ELM Message Timeout
     *
     * @return minimum (configured) ELM timeout value [ms]
     */
    public int getElmTimeoutMin()
    {
        return ELM_TIMEOUT_MIN;
    }

    /**
     * Set min. (configured) ELM Message Timeout
     *
     * @param elmTimeoutMin minimum (configured) ELM timeout value [ms]
     */
    public void setElmTimeoutMin(int elmTimeoutMin)
    {
        log.info(String.format("ELM min timeout: %d -> %d",
                ELM_TIMEOUT_MIN, elmTimeoutMin));
        ELM_TIMEOUT_MIN = elmTimeoutMin;
    }

    /**
     * Initialize timing hadler
     */
    public void initialize()
    {
        if (mode == AdaptTimingMode.SOFTWARE)
        {
            // ... reset learned minimum timeout ...
            setElmTimeoutLrnLow(getElmTimeoutMin());
            // set default timeout
            setElmMsgTimeout(ELM_TIMEOUT_DEFAULT);
            // switch OFF ELM internal adaptive timing
            mCommandSender.pushCommand(CMD.ADAPTTIMING, 0);
        }
        else
        {
            mCommandSender.pushCommand(CMD.ADAPTTIMING, mode.ordinal());
        }
    }
    public void setMode(AdaptTimingMode mode){
        log.info(String.format("AdaptiveTiming mode: %s -> %s",
                this.mode.toString(),
                mode.toString()));
        this.mode = mode;
        initialize();
    }
    /**
     * Adapt ELM message timeout
     *
     * @param increaseTimeout increase/decrease timeout
     */
    void adapt(boolean increaseTimeout)
    {
        if (mode != AdaptTimingMode.SOFTWARE) { return; }
        if (increaseTimeout)
        {
            // increase OBD timeout since we may expect answers too fast
            if ((elmMsgTimeout + ELM_TIMEOUT_RES) < ELM_TIMEOUT_MAX)
            {
                // increase timeout, since we have just timed out
                setElmMsgTimeout(elmMsgTimeout + ELM_TIMEOUT_RES);
                // ... and limit MIN timeout for this session
                setElmTimeoutLrnLow(elmMsgTimeout);
            }
        }
        else
        {
            // reduce OBD timeout towards minimum limit
            if ((elmMsgTimeout - ELM_TIMEOUT_RES) >= getElmTimeoutLrnLow())
            {
                setElmMsgTimeout(elmMsgTimeout - ELM_TIMEOUT_RES);
            }

        }
    }

    /**
     * LOW Learn value ELM Message Timeout
     *
     * @return currently learned timout value [ms]
     */
    public int getElmTimeoutLrnLow()
    {
        return ELM_TIMEOUT_LRN_LOW;
    }

    /**
     * set LOW Learn value ELM Message Timeout
     *
     * @param elmTimeoutLrnLow new learn value [ms]
     */
    public void setElmTimeoutLrnLow(int elmTimeoutLrnLow)
    {
        log.info(String.format("ELM learn timeout: %d -> %d",
                ELM_TIMEOUT_LRN_LOW, elmTimeoutLrnLow));
        ELM_TIMEOUT_LRN_LOW = elmTimeoutLrnLow;
    }

    /**
     * Set message timeout to ELM adapter to wait for valid response from vehicle
     * If this timeout expires before a valid response is received from the
     * vehicle, the ELM adapter will respond with "NO DATA"
     *
     * @param newTimeout desired timeout in milliseconds
     */
    public void setElmMsgTimeout(int newTimeout)
    {
        if (newTimeout > 0 && newTimeout != elmMsgTimeout)
        {
            log.info("ELM Timeout: " + elmMsgTimeout + " -> " + newTimeout);
            // set the timeout variable
            elmMsgTimeout = newTimeout;
            // queue the new timeout message
            mCommandSender.sendCommand(CMD.SETTIMEOUT, newTimeout / 4);
        }
    }
}