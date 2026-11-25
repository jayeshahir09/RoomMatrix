package com.roommatrix.backend.repository;

import com.roommatrix.backend.entity.PendingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {

    Optional<PendingRegistration> findByMobileNumber(String mobileNumber);

    Optional<PendingRegistration> findByEmail(String email);

    void deleteByMobileNumber(String mobileNumber);

    void deleteByExpiresAtBefore(LocalDateTime now);
}
