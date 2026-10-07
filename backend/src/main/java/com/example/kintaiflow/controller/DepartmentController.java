package com.example.kintaiflow.controller;

import com.example.kintaiflow.dto.DepartmentListResponse;
import com.example.kintaiflow.repository.DepartmentRepository;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 部署一覧取得（認証必須・全ロール可）。ユーザー管理・月次集計のプルダウンに使う。 */
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentRepository departmentRepository;

    public DepartmentController(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @GetMapping
    public DepartmentListResponse list() {
        return new DepartmentListResponse(departmentRepository.findAll(Sort.by("id")).stream()
                .map(d -> new DepartmentListResponse.Item(d.getId(), d.getName()))
                .toList());
    }
}
