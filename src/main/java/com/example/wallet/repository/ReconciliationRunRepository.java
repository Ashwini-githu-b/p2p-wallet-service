package com.example.wallet.repository;

import com.example.wallet.entity.ReconciliationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {
    Optional<ReconciliationRun> findFirstByOrderByStartedAtDesc();
}

