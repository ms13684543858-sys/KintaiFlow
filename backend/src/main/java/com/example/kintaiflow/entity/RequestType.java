package com.example.kintaiflow.entity;

/** 申請種別（requests.request_type）。 */
public final class RequestType {
    public static final String LEAVE = "LEAVE";
    public static final String CLOCK_CORRECTION = "CLOCK_CORRECTION";
    public static final String BREAK_CORRECTION = "BREAK_CORRECTION";   // 休憩・離席の修正申請

    private RequestType() {}
}
