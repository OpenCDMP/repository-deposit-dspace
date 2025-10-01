package org.opencdmp.deposit.dspacerepository.configuration;

import org.opencdmp.deposit.dspacerepository.configuration.semantics.SemanticsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({SemanticsProperties.class})
public class GenericConfiguration {
}
