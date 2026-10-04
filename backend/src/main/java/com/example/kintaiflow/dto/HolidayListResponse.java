package com.example.kintaiflow.dto;

import java.util.List;

/** ONL-016 祝日一覧の応答。 */
public record HolidayListResponse(Integer year, List<HolidayResponse> holidays) {
}
