package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.Director;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DirectorRepository extends JpaRepository<Director, Long> {
    Optional<Director> findByEmailIgnoreCase(String email);
}
