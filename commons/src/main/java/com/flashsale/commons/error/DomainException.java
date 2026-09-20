package com.flashsale.commons.error;

import org.springframework.http.HttpStatus;
import java.util.LinkedHashMap;
import java.util.Map;

public class DomainException extends RuntimeException {

    private final HttpStatus status;
    private final String problemType;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    public DomainException(HttpStatus status, String problemType, String detail) {
        super(detail);
        this.status = status;
        this.problemType = problemType;
    }

    public DomainException with(String key, Object value) {
        properties.put(key, value);
        return this;
    }

    public HttpStatus getStatus() { return status; }
    public String getProblemType() { return problemType; }
    public Map<String, Object> getProperties() { return properties; }
}
