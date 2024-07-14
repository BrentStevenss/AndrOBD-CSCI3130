package com.fr3ts0n.ecu;

/**
 * possible communication states
 */
public enum STAT {
        UNDEFINED("Undefined"),
        INITIALIZING("Initializing"),
        INITIALIZED("Initialized"),
        ECU_DETECT("ECU detect"),
        ECU_DETECTED("ECU detected"),
        CONNECTING("Connecting"),
        CONNECTED("Connected"),
        NODATA("No data"),
        STOPPED("Stopped"),
        DISCONNECTED("Disconnected"),
        BUSERROR("BUS error"),
        DATAERROR("DATA error"),
        RXERROR("RX error"),
        ERROR("Error");
        private final String elmState;

        STAT(String state)
        {
            elmState = state;
        }

        @Override
        public String toString()
        {
            return elmState;
        }
    }

