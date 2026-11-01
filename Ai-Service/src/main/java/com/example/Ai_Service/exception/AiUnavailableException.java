package com.example.Ai_Service.exception;

import com.project.web.exception.ApiException;
import org.springframework.http.HttpStatus;

// AI plan phase 1: Gemini or another service failed. It is not the user's mistake and not our bug, so it is not a 500.
// common-web's GlobalExceptionHandler turns any ApiException into the normal error answer.
public class AiUnavailableException extends ApiException {

    public AiUnavailableException(String errorCode, String message, HttpStatus status, Throwable cause) {
        super(errorCode, message, status, cause);
    }
}
