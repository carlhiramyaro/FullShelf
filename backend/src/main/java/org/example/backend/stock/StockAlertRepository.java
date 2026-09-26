package org.example.backend.stock;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StockAlertRepository extends JpaRepository<StockAlert, Long> {
}
