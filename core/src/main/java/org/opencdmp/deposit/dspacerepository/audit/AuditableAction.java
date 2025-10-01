package org.opencdmp.deposit.dspacerepository.audit;


import gr.cite.tools.logging.EventId;

public class AuditableAction {

    public static final EventId Deposit_Deposit = new EventId(1000, "Deposit_Deposit");
    public static final EventId Deposit_Authenticate = new EventId(1001, "Deposit_Authenticate");
    public static final EventId Deposit_GetConfiguration = new EventId(1002, "Deposit_GetConfiguration");
    public static final EventId Deposit_GetLogo = new EventId(1003, "Deposit_GetLogo");

    
}
