package org.opencdmp.deposit.dspacerepository.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PatchEntity {

    private String op;
    private String path;
    private List<Map<String, Object>> value;

    public String getOp() {
        return op;
    }
    public void setOp(String op) {
        this.op = op;
    }

    public String getPath() {
        return path;
    }
    public void setPath(String path) {
        this.path = path;
    }

    public List<Map<String, Object>> getValue() {
        return value;
    }

    public void setValue(List<Map<String, Object>> value) {
        this.value = value;
    }
}
