package org.opencdmp.deposit.dspacerepository.service.dspace;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({DspaceServiceProperties.class})
public class DspaceServiceConfiguration {
}
