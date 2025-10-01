package org.opencdmp.deposit.dspacerepository.configuration.identifier;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "identifiers")
public class IdentifierProperties {
    private List<String> related;

    private List<String> scheme;

    public List<String> getRelated() {
        return related;
    }

    public void setRelated(List<String> related) {
        this.related = related;
    }


    public List<String> getScheme() {
        return scheme;
    }

    public void setScheme(List<String> scheme) {
        this.scheme = scheme;
    }
}
