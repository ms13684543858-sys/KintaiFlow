package com.example.kintaiflow.entity;

import java.util.List;

/** 申請状態（requests.status）。 */
public final class RequestStatus {
    public static final String DRAFT = "DRAFT";
    public static final String PENDING = "PENDING";
    public static final String RETURNED = "RETURNED";
    public static final String REJECTED = "REJECTED";
    public static final String APPROVED = "APPROVED";
    public static final String WITHDRAWN = "WITHDRAWN";
    /** 期間重複・残日数の引当の対象となる「有効な」状態。 */
    public static final List<String> BLOCKING = List.of(PENDING, APPROVED);

    private RequestStatus() {}
}
