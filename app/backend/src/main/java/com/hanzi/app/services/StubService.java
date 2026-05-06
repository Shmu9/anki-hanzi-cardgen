package com.hanzi.app.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class StubService {
    private final String serviceName;
    private final List<String> plannedEndpoints;

    StubService(String serviceName, List<String> plannedEndpoints) {
        this.serviceName = serviceName;
        this.plannedEndpoints = List.copyOf(plannedEndpoints);
    }

    public Map<String, Object> notImplemented(String operation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("service", serviceName);
        payload.put("operation", operation);
        payload.put("implemented", false);
        payload.put("status", "scaffolded");
        payload.put("message", serviceName + " is planned but not implemented yet.");
        payload.put("planned_endpoints", plannedEndpoints);
        return payload;
    }
}
