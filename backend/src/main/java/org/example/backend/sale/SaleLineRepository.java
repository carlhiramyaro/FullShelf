package org.example.backend.sale;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SaleLineRepository extends JpaRepository<SaleLine, Long> {

    List<SaleLine> findBySaleId(Long saleId);
}
