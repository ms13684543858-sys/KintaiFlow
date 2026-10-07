-- V5: 休憩・離席の修正申請（request_type = BREAK_CORRECTION）。
--   申請内容は 対象日（start_date）＋ 種別（BREAK / AWAY）＋ 修正後の開始・終了時刻。最終承認で attendance_breaks に反映する。
ALTER TABLE requests ADD COLUMN corrected_break_kind VARCHAR(10);
ALTER TABLE requests ADD COLUMN corrected_break_start TIMESTAMP;
ALTER TABLE requests ADD COLUMN corrected_break_end TIMESTAMP;
ALTER TABLE requests DROP CONSTRAINT ck_req_type;
ALTER TABLE requests ADD CONSTRAINT ck_req_type CHECK (request_type IN ('LEAVE','CLOCK_CORRECTION','BREAK_CORRECTION'));
ALTER TABLE requests ADD CONSTRAINT ck_req_break_kind CHECK (corrected_break_kind IS NULL OR corrected_break_kind IN ('BREAK','AWAY'));
