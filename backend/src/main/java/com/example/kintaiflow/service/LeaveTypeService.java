package com.example.kintaiflow.service;

import com.example.kintaiflow.dto.*;
import com.example.kintaiflow.entity.LeaveType;
import com.example.kintaiflow.exception.BusinessException;
import com.example.kintaiflow.repository.LeaveTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** ONL-015 休暇種別管理 / ONL-021 有効休暇種別一覧取得。 */
@Service
@Transactional(readOnly = true)
public class LeaveTypeService {

    private static final Logger log = LoggerFactory.getLogger(LeaveTypeService.class);
    /** 年次有給休暇の名称（固定項目の保護対象）。 */
    private static final String ANNUAL_LEAVE_NAME = "年次有給休暇";

    private final LeaveTypeRepository leaveTypeRepository;

    public LeaveTypeService(LeaveTypeRepository leaveTypeRepository) {
        this.leaveTypeRepository = leaveTypeRepository;
    }

    public LeaveTypeListResponse list(boolean activeOnly) {
        List<LeaveType> rows = activeOnly
                ? leaveTypeRepository.findByIsActiveTrueOrderByIdAsc()
                : leaveTypeRepository.findAllByOrderByIdAsc();
        return new LeaveTypeListResponse(rows.stream().map(this::toResponse).toList());
    }

    public LeaveTypeDetailResponse get(Long id) {
        LeaveType t = leaveTypeRepository.findById(id).orElseThrow(LeaveTypeService::notFound);
        return new LeaveTypeDetailResponse(toResponse(t));
    }

    /** ONL-021 申請画面用。有効な休暇種別のみ 5 項目で返す。 */
    public LeaveTypeOptionListResponse listActiveOptions() {
        List<LeaveType> rows = leaveTypeRepository.findByIsActiveTrueOrderByIdAsc();
        return new LeaveTypeOptionListResponse(rows.stream().map(this::toOption).toList());
    }

    @Transactional
    public LeaveTypeDetailResponse create(LeaveTypeRequest req) {
        String name = req.name().strip();
        if (leaveTypeRepository.existsByName(name)) throw duplicated("名称");
        LeaveType t = new LeaveType();
        apply(t, name, req);
        try {
            t = leaveTypeRepository.saveAndFlush(t);
        } catch (DataIntegrityViolationException e) {
            throw duplicated("名称");
        }
        log.info("LeaveType created: id={}", t.getId());
        return new LeaveTypeDetailResponse(toResponse(t));
    }

    @Transactional
    public LeaveTypeDetailResponse update(Long id, LeaveTypeRequest req) {
        LeaveType t = leaveTypeRepository.findByIdForUpdate(id).orElseThrow(LeaveTypeService::notFound);
        String name = req.name().strip();
        if (ANNUAL_LEAVE_NAME.equals(t.getName())
                && (!t.getName().equals(name)
                || !Boolean.TRUE.equals(req.isPaid())
                || !"LIMITED".equals(req.maxDaysRule())
                || !Boolean.TRUE.equals(req.isActive()))) {
            throw new BusinessException("E-016",
                    "年次有給休暇の名称・有給区分・日数ルール・有効フラグは固定のため、この操作は行えません。",
                    HttpStatus.CONFLICT);
        }
        if (leaveTypeRepository.existsByNameAndIdNot(name, id)) throw duplicated("名称");
        apply(t, name, req);
        try {
            t = leaveTypeRepository.saveAndFlush(t);
        } catch (DataIntegrityViolationException e) {
            throw duplicated("名称");
        }
        log.info("LeaveType updated: id={}, isActive={}", id, t.getIsActive());
        return new LeaveTypeDetailResponse(toResponse(t));
    }

    private void apply(LeaveType t, String name, LeaveTypeRequest req) {
        t.setName(name);
        t.setLegalBasis(blankToNull(req.legalBasis()));
        t.setIsPaid(req.isPaid());
        t.setMaxDaysRule(req.maxDaysRule());
        t.setAllowHalfDay(req.allowHalfDay());
        t.setIsActive(req.isActive());
    }

    private LeaveTypeResponse toResponse(LeaveType t) {
        return new LeaveTypeResponse(t.getId(), t.getName(), t.getLegalBasis(), t.getIsPaid(),
                t.getMaxDaysRule(), t.getAllowHalfDay(), t.getIsActive());
    }

    private LeaveTypeOptionResponse toOption(LeaveType t) {
        return new LeaveTypeOptionResponse(t.getId(), t.getName(), t.getIsPaid(),
                t.getMaxDaysRule(), t.getAllowHalfDay());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.strip();
    }

    private static BusinessException notFound() {
        return new BusinessException("E-015", "対象のデータが見つかりません。", HttpStatus.NOT_FOUND);
    }

    private static BusinessException duplicated(String item) {
        return new BusinessException("E-013", item + "は既に登録されています。", HttpStatus.CONFLICT);
    }
}
