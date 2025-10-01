package org.opencdmp.deposit.dspacerepository.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PatchBooleanEntity {

    private String op;
    private String path;
    private boolean value;

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

    public boolean getValue() {
        return value;
    }
    public void setValue(boolean value) {
        this.value = value;
    }
}
