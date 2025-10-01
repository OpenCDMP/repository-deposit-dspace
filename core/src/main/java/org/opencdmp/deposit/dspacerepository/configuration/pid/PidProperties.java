package org.opencdmp.deposit.dspacerepository.configuration.pid;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "pid")
public class PidProperties {

    private List<String> acceptedTypes;
    private PidFieldNames fields;

    public List<String> getAcceptedTypes() {
        return acceptedTypes;
    }

    public PidFieldNames getFields() {
        return fields;
    }

    public void setAcceptedTypes(List<String> acceptedTypes) {
        this.acceptedTypes = acceptedTypes;
    }

    public void setFields(PidFieldNames fields) {
        this.fields = fields;
    }

    public static class PidFieldNames {
        private String pidName;
        private String pidTypeName;

        public String getPidName() {
            return pidName;
        }

        public void setPidName(String pidName) {
            this.pidName = pidName;
        }

        public String getPidTypeName() {
            return pidTypeName;
        }

        public void setPidTypeName(String pidTypeName) {
            this.pidTypeName = pidTypeName;
        }
    }
}
