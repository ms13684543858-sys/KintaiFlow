package com.example.kintaiflow.entity;

/** 承認ステップの状態（approval_steps.status）。 */
public final class StepStatus {
    public static final String WAITING = "WAITING";
    public static final String APPROVED = "APPROVED";
    public static final String RETURNED = "RETURNED";
    public static final String REJECTED = "REJECTED";

    private StepStatus() {}
}
