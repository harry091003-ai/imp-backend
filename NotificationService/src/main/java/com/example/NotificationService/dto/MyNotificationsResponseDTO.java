package com.example.NotificationService.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The answer of GET /notifications/me (step 7).
 * total = how many notifications you have; page/size = which part; items = this page, newest first.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MyNotificationsResponseDTO {
    private long total;
    private int page;
    private int size;
    private List<NotificationItemDTO> items;
}
