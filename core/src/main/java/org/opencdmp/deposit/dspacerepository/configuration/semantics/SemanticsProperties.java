package org.opencdmp.deposit.dspacerepository.configuration.semantics;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "semantics")
public class SemanticsProperties {

   private  List<PathName> available;

    public List<PathName> getAvailable() {
        return available;
    }

    public void setAvailable(List<PathName> available) {
        this.available = available;
    }

    public static class PathName {
        private String name;
        private String path;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }
}
