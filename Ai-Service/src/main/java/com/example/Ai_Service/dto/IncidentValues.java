package com.example.Ai_Service.dto;

// AI plan phase 1: the values a request may use. They must match IncidentService's enums (IncidentCategory, ServiceName,
// Severity, Priority),
// because that is what is stored and searched. A new value there must be added here and in the classification prompt.
// Without this check a typo missed the filter silently and searched everything, on a paid call.
public final class IncidentValues {

    public static final String CATEGORY = "API|DATABASE|NETWORK|INFRASTRUCTURE|HARDWARE|CUSTOMER|SAFETY|OTHER";
    public static final String CATEGORY_MESSAGE = "category must be one of: " + CATEGORY;

    public static final String SERVICE = "USER_SERVICE|EMAIL_SERVICE|NOTIFICATION_SERVICE|PAYMENT_SERVICE|AUTH_SERVICE"
            + "|APIGATEWAY_SERVICE|REPORTING_SERVICE|OTHERS";
    public static final String SERVICE_MESSAGE = "serviceName must be one of: " + SERVICE;

    public static final String SEVERITY = "LOW|MEDIUM|HIGH|CRITICAL";
    public static final String PRIORITY = "LOW|MEDIUM|HIGH|CRITICAL";

    private IncidentValues() {
    }
}
