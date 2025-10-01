package org.opencdmp.deposit.dspacerepository.service.dspace;

import org.opencdmp.depositbase.repository.DepositConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "dspace")
public class DspaceServiceProperties {

    private String collection;

    private String username;

    private String password;

    private String logo;

    private String domain;

    private DepositConfiguration depositConfiguration;

    private int maxInMemorySizeInBytes;

    private String defaultType;

    private List<String> acceptedTypeCodes;

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDefaultType() {
        return defaultType;
    }

    public void setDefaultType(String defaultType) {
        this.defaultType = defaultType;
    }

    public List<String> getAcceptedTypeCodes() {
        return acceptedTypeCodes;
    }

    public void setAcceptedTypeCodes(List<String> acceptedTypeCodes) {
        this.acceptedTypeCodes = acceptedTypeCodes;
    }

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public DepositConfiguration getDepositConfiguration() {
        return depositConfiguration;
    }

    public void setDepositConfiguration(DepositConfiguration depositConfiguration) {
        this.depositConfiguration = depositConfiguration;
    }

    public int getMaxInMemorySizeInBytes() {
        return maxInMemorySizeInBytes;
    }

    public void setMaxInMemorySizeInBytes(int maxInMemorySizeInBytes) {
        this.maxInMemorySizeInBytes = maxInMemorySizeInBytes;
    }
}
