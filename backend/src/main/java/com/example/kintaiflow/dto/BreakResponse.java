package com.example.kintaiflow.dto;

import java.util.List;

/** 休憩・離席の開始／終了後の本日の状態。openKind は継続中の区間の種別（無ければ null）。 */
public record BreakResponse(String workDate, String openKind, List<BreakItem> breaks) {}
