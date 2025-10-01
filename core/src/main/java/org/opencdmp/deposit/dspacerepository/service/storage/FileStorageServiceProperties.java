package org.opencdmp.deposit.dspacerepository.service.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "file.storage")
public class FileStorageServiceProperties {
    private final String temp;
    private final String transientPath;

    @ConstructorBinding
    public FileStorageServiceProperties(String temp, String transientPath) {
        this.temp = temp;
        this.transientPath = transientPath;
    }

    public String getTemp() {
        return temp;
    }

    public String getTransientPath() {
        return transientPath;
    }
}
