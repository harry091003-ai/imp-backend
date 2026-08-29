package com.example.ReportingService.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class SLAConfiguration {

    public static long getResolutionSLA_INSeconds(String priority){
        long resolutionLimitValue=switch(priority){
         case "HIGH"->864000;
         case "MEDIUM"->50;
         case "LOW"->864000;
          default -> 0;
        };
          return resolutionLimitValue;
    }

    public static long getAssignmentSLA_INSeconds(String priority){
     long assignmentLimitValue=   switch(priority){
            case "HIGH"->30;
            case "MEDIUM"->40;
            case "LOW"->86400;
            default->3600;
        };
     return assignmentLimitValue;
    }
}
