package org.opencdmp.deposit.dspacerepository.configuration.funder;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "funder")
public class FunderProperties {

    private List<DoiFunder> available;

    public List<DoiFunder> getAvailable() {
        return available;
    }

    public void setAvailable(List<DoiFunder> available) {
        this.available = available;
    }

    public static class DoiFunder {
        private String funder;
        private String doi;

        public String getFunder() {
            return funder;
        }

        public String getDoi() {
            return doi;
        }

        public void setFunder(String funder) {
            this.funder = funder;
        }

        public void setDoi(String doi) {
            this.doi = doi;
        }
    }
}
