package org.opencdmp.deposit.dspacerepository.service.dspace;

import org.opencdmp.commonmodels.models.deposit.DepositPreviewModel;
import org.opencdmp.commonmodels.models.deposit.PreviewFieldOption;
import org.opencdmp.depositbase.repository.DepositAccount;
import org.opencdmp.depositbase.repository.DepositConfiguration;
import org.opencdmp.depositbase.repository.PlanDepositModel;

import java.util.List;

public interface DspaceDepositService {
	String deposit(PlanDepositModel planDepositModel);

	DepositConfiguration getConfiguration();

	DepositAccount account(PlanDepositModel planDepositModel);

	DepositPreviewModel preview(PlanDepositModel planDepositModel);

	List<PreviewFieldOption> searchVocabulary(String fieldCode, String like);


	String authenticate(String code);

	String getLogo();
}
