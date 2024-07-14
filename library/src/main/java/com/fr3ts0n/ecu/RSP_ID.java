package com.fr3ts0n.ecu;
/**
 * possible ELM responses and ID's
 */
public enum RSP_ID
    {
        PROMPT(">"),
        OK("OK"),
        MODEL("ELM"),
        NODATA("NODATA"),
        SEARCH("SEARCHING"),
        ERROR("ERROR"),
        NOCONN("UNABLE"),
        NOCONN2("NABLETO"),
        CANERROR("CANERROR"),
        BUSBUSY("BUSBUSY"),
        BUSERROR("BUSERROR"),
        BUSINIERR("BUSINIT:ERR"),
        BUSINIERR2("BUSINIT:BUS"),
        BUSINIERR3("BUSINIT:...ERR"),
        FBERROR("FBERROR"),
        DATAERROR("DATAERROR"),
        BUFFERFULL("BUFFERFULL"),
        STOPPED("STOPPED"),
        RXERROR("<"),
        QMARK("?"),
        UNKNOWN("");
        private final String response;

        RSP_ID(String response)
        {
            this.response = response;
        }

        @Override
        public String toString()
        {
            return response;
        }
    }

