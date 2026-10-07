package com.example.kintaiflow.dto;

/** 休憩・日中離席の1区間。kind は BREAK / AWAY、endAt が null なら継続中。 */
public record BreakItem(String kind, String startAt, String endAt) {}
