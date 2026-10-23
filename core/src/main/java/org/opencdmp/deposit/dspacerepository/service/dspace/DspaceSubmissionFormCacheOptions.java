package org.opencdmp.deposit.dspacerepository.service.dspace;

import gr.cite.tools.cache.CacheOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "cache.dspace-submission-form")
public class DspaceSubmissionFormCacheOptions extends CacheOptions {
}
