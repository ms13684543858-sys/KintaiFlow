package com.example.kintaiflow.repository;

import com.example.kintaiflow.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    List<Holiday> findByHolidayDateBetweenOrderByHolidayDate(LocalDate from, LocalDate to);

    boolean existsByHolidayDate(LocalDate holidayDate);

    @Modifying
    @Query("delete from Holiday h where h.id = :id")
    int deleteHolidayById(@Param("id") Long id);
}
