package com.example.kintaiflow.service;

import com.example.kintaiflow.dto.HolidayDetailResponse;
import com.example.kintaiflow.dto.HolidayListResponse;
import com.example.kintaiflow.dto.HolidayRequest;
import com.example.kintaiflow.dto.HolidayResponse;
import com.example.kintaiflow.entity.Holiday;
import com.example.kintaiflow.exception.BusinessException;
import com.example.kintaiflow.repository.HolidayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 祝日・会社休日（ONL-005 が参照する検索系 / ONL-016 祝日管理の list・create・delete）。 */
@Service
public class HolidayService {

    private static final Logger log = LoggerFactory.getLogger(HolidayService.class);
    private static final int YEAR_MIN = 2000;
    private static final int YEAR_MAX = 2099;

    private final HolidayRepository holidayRepository;
    private final Clock clock;

    public HolidayService(HolidayRepository holidayRepository, Clock clock) {
        this.holidayRepository = holidayRepository;
        this.clock = clock;
    }

    /** 期間 [from, to]（両端含む）に含まれる祝日の日付集合。 */
    @Transactional(readOnly = true)
    public Set<LocalDate> getHolidayDates(LocalDate from, LocalDate to) {
        return holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(from, to).stream()
                .map(Holiday::getHolidayDate).collect(Collectors.toSet());
    }

    /** 指定日が祝日・会社休日として登録されているか（土日の判定はしない）。 */
    @Transactional(readOnly = true)
    public boolean isHoliday(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return holidayRepository.existsByHolidayDate(date);
    }

    /** 指定年（未指定は本日の年）の祝日を日付昇順で返す。 */
    @Transactional(readOnly = true)
    public HolidayListResponse list(Integer year) {
        int y = (year != null) ? year : LocalDate.now(clock).getYear();
        if (y < YEAR_MIN || y > YEAR_MAX) {
            throw new BusinessException("E-002",
                    "対象年は2000年〜2099年の範囲で指定してください。", HttpStatus.BAD_REQUEST);
        }
        List<Holiday> rows = holidayRepository
                .findByHolidayDateBetweenOrderByHolidayDate(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));
        return new HolidayListResponse(y, rows.stream().map(this::toResponse).toList());
    }

    @Transactional
    public HolidayDetailResponse create(HolidayRequest req) {
        LocalDate d = req.holidayDate();
        if (d.getYear() < YEAR_MIN || d.getYear() > YEAR_MAX) {
            throw new BusinessException("E-002",
                    "日付は2000年〜2099年の範囲で指定してください。", HttpStatus.BAD_REQUEST);
        }
        if (holidayRepository.existsByHolidayDate(d)) {
            throw new BusinessException("E-013", "日付は既に登録されています。", HttpStatus.CONFLICT);
        }
        Holiday h = new Holiday();
        h.setHolidayDate(d);
        h.setName(req.name().strip());
        try {
            h = holidayRepository.saveAndFlush(h);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("E-013", "日付は既に登録されています。", HttpStatus.CONFLICT);
        }
        log.info("Holiday created: id={}, date={}", h.getId(), h.getHolidayDate());
        return new HolidayDetailResponse(toResponse(h));
    }

    @Transactional
    public HolidayDetailResponse delete(Long id) {
        Holiday h = holidayRepository.findById(id).orElseThrow(HolidayService::notFound);
        int n = holidayRepository.deleteHolidayById(id);
        if (n == 0) throw notFound();
        log.info("Holiday deleted: id={}, date={}", id, h.getHolidayDate());
        return new HolidayDetailResponse(toResponse(h));
    }

    private HolidayResponse toResponse(Holiday h) {
        return new HolidayResponse(h.getId(), h.getHolidayDate().toString(), h.getName());
    }

    private static BusinessException notFound() {
        return new BusinessException("E-015", "対象のデータが見つかりません。", HttpStatus.NOT_FOUND);
    }
}
