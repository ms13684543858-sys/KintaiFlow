package com.example.kintaiflow.dto;

import java.util.List;

/** 部署一覧のレスポンス（画面のプルダウン用）。 */
public record DepartmentListResponse(List<Item> departments) {
    public record Item(Long id, String name) {
    }
}
