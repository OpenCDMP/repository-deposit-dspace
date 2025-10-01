package org.opencdmp.deposit.dspacerepository.service.dspace;

import org.opencdmp.depositbase.repository.DepositConfiguration;
import org.opencdmp.depositbase.repository.PlanDepositModel;

public interface DspaceDepositService {
	String deposit(PlanDepositModel planDepositModel) throws Exception;

	DepositConfiguration getConfiguration();

	String authenticate(String code);

	String getLogo();
}
