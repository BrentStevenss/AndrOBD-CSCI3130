package com.fr3ts0n.ecu;
/**
 * ELM protocol ID's
 */
public enum PROT
{
    ELM_PROT_AUTO("Automatic"),
    ELM_PROT_J1850PWM("SAE J1850 PWM (41.6 KBaud)"),
    ELM_PROT_J1850VPW("SAE J1850 VPW (10.4 KBaud)"),
    ELM_PROT_9141_2("ISO 9141-2 (5 Baud Init)"),
    ELM_PROT_14230_4("ISO 14230-4 KWP (5 Baud Init)"),
    ELM_PROT_14230_4F("ISO 14230-4 KWP (fast Init)"),
    ELM_PROT_15765_11_F("ISO 15765-4 CAN (11 Bit ID, 500 KBit)"),
    ELM_PROT_15765_29_F("ISO 15765-4 CAN (29 Bit ID, 500 KBit)"),
    ELM_PROT_15765_11_S("ISO 15765-4 CAN (11 Bit ID, 250 KBit)"),
    ELM_PROT_15765_29_S("ISO 15765-4 CAN (29 Bit ID, 250 KBit)"),
    ELM_PROT_J1939_29_S("SAE J1939 CAN (29 bit ID, 250* kbaud)"),
    ELM_PROT_USER1_CAN_11_S("User1 CAN (11* bit ID, 125* kbaud)"),
    ELM_PROT_USER2_CAN_11_S("User2 CAN (11* bit ID, 50* kbaud)");
    private final String description;

    PROT(String _description)
    {
        description = _description;
    }

    @Override
    public String toString()
    {
        return description;
    }
}
