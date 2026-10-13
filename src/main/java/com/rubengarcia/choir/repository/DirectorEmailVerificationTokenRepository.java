package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.DirectorEmailVerificationToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DirectorEmailVerificationTokenRepository
        extends JpaRepository<DirectorEmailVerificationToken, Long> {

    void deleteAllByDirector_Id(Long directorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token
            from DirectorEmailVerificationToken token
            join fetch token.director
            where token.tokenHash = :tokenHash
            """)
    Optional<DirectorEmailVerificationToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash);
}
