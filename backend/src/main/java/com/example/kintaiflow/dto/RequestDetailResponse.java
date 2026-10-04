package com.example.kintaiflow.dto;

import java.math.BigDecimal;
import java.util.List;

/** ONL-007 申請詳細のレスポンス。applicantBalance は LEAVE かつ LIMITED のみ（他は null）。 */
public record RequestDetailResponse(RequestDetail request, List<StepDetail> steps, BigDecimal applicantBalance) {
}
