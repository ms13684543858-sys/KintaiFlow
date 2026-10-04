package com.example.kintaiflow.service;

import com.example.kintaiflow.entity.ApprovalStep;
import com.example.kintaiflow.entity.Request;
import com.example.kintaiflow.entity.StepStatus;
import com.example.kintaiflow.entity.User;
import com.example.kintaiflow.repository.ApprovalStepRepository;
import com.example.kintaiflow.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 承認ルート（上長→管理者の2段）の作成と現在の承認者の特定。ONL-005 / ONL-022 が作成、ONL-008 / 010〜012 が参照する。 */
@Service
public class ApprovalRouteService {

    public static final int STEP_MANAGER = 1;
    public static final int STEP_ADMIN = 2;

    private final ApprovalStepRepository approvalStepRepository;
    private final UserRepository userRepository;

    public ApprovalRouteService(ApprovalStepRepository approvalStepRepository, UserRepository userRepository) {
        this.approvalStepRepository = approvalStepRepository;
        this.userRepository = userRepository;
    }

    /** 承認ステップを1〜2行登録して返す。上長なし／INACTIVE は2段目のみ（呼び出し元の @Transactional に参加）。 */
    public List<ApprovalStep> createSteps(Request request, User applicant) {
        Long managerId = findActiveManagerId(applicant);
        List<ApprovalStep> steps = new ArrayList<>();
        if (managerId != null) {
            steps.add(newStep(request.getId(), STEP_MANAGER, managerId));
        }
        steps.add(newStep(request.getId(), STEP_ADMIN, null));
        return approvalStepRepository.saveAll(steps);
    }

    /** 処理待ちの段の通知先。承認者確定済みはその1名、未設定（管理者段）は申請者以外の有効な全管理者。 */
    public List<Long> resolveCurrentApproverIds(ApprovalStep currentStep, Long applicantId) {
        if (currentStep.getApproverId() != null) {
            return List.of(currentStep.getApproverId());
        }
        return userRepository.findByRoleAndStatus("ADMIN", "ACTIVE").stream()
                .map(User::getId)
                .filter(id -> !id.equals(applicantId))
                .toList();
    }

    private ApprovalStep newStep(Long requestId, int stepNo, Long approverId) {
        ApprovalStep s = new ApprovalStep();
        s.setRequestId(requestId);
        s.setStepNo(stepNo);
        s.setApproverId(approverId);
        s.setStatus(StepStatus.WAITING);
        return s;
    }

    private Long findActiveManagerId(User applicant) {
        if (applicant.getManagerId() == null || applicant.getManagerId().equals(applicant.getId())) {
            return null;
        }
        return userRepository.findById(applicant.getManagerId())
                .filter(m -> "ACTIVE".equals(m.getStatus()))
                .map(User::getId)
                .orElse(null);
    }
}
