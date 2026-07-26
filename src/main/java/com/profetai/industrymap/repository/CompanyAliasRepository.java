package com.profetai.industrymap.repository;

import com.profetai.industrymap.model.CompanyAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyAliasRepository extends JpaRepository<CompanyAlias, Long> {

    Optional<CompanyAlias> findByNormalizedAlias(String normalizedAlias);

    boolean existsByNormalizedAlias(String normalizedAlias);

    List<CompanyAlias> findByCompanyId(Long companyId);
}
