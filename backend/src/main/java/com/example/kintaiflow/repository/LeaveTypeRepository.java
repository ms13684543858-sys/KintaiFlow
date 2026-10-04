package com.example.kintaiflow.repository;

import com.example.kintaiflow.entity.LeaveType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

    List<LeaveType> findAllByOrderByIdAsc();

    List<LeaveType> findByIsActiveTrueOrderByIdAsc();

    Optional<LeaveType> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from LeaveType t where t.id = :id")
    Optional<LeaveType> findByIdForUpdate(@Param("id") Long id);
}
