package org.opencdmp.deposit.controller;

import gr.cite.tools.auditing.AuditService;
import gr.cite.tools.logging.LoggerService;
import gr.cite.tools.logging.MapLogEntry;
import org.opencdmp.commonmodels.models.plan.PlanModel;
import org.opencdmp.deposit.dspacerepository.audit.AuditableAction;
import org.opencdmp.depositbase.repository.DepositConfiguration;
import org.opencdmp.deposit.dspacerepository.service.dspace.DspaceDepositService;
import org.opencdmp.depositbase.repository.PlanDepositModel;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.AbstractMap;
import java.util.Map;

@RestController
@RequestMapping("/api/deposit")
public class DepositController implements org.opencdmp.depositbase.repository.DepositController {
    private static final LoggerService logger = new LoggerService(LoggerFactory.getLogger(DepositController.class));

    private final DspaceDepositService depositClient;

    private final AuditService auditService;

    @Autowired
    public DepositController(DspaceDepositService depositClient, AuditService auditService) {
        this.depositClient = depositClient;
	    this.auditService = auditService;
    }

    public String deposit(@RequestBody PlanDepositModel planDepositModel) {
        logger.debug(new MapLogEntry("deposit " + PlanModel.class.getSimpleName()).And("planDepositModel", planDepositModel));

        String doiId = depositClient.deposit(planDepositModel);
        
        this.auditService.track(AuditableAction.Deposit_Deposit, Map.ofEntries(
                new AbstractMap.SimpleEntry<String, Object>("planDepositModel", planDepositModel)
        ));
        return doiId;
    }

    public String authenticate(@RequestParam("authToken") String code) {
        logger.debug(new MapLogEntry("authenticate " + PlanModel.class.getSimpleName()));

        String token = depositClient.authenticate(code);

        this.auditService.track(AuditableAction.Deposit_Authenticate);
        
        return token;
    }

    public DepositConfiguration getConfiguration() {
        logger.debug(new MapLogEntry("getConfiguration " + PlanModel.class.getSimpleName()));
        
        DepositConfiguration configuration = depositClient.getConfiguration();
        
        this.auditService.track(AuditableAction.Deposit_GetConfiguration);
        
        return configuration;
    }

    public String getLogo() {
        logger.debug(new MapLogEntry("getLogo " + PlanModel.class.getSimpleName()));

        String logo = depositClient.getLogo();

        this.auditService.track(AuditableAction.Deposit_GetLogo);

        return logo;
    }

}
