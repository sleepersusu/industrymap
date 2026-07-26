package com.profetai.industrymap.repository;

import com.profetai.industrymap.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByNormalizedName(String normalizedName);

    boolean existsByNormalizedName(String normalizedName);
}
