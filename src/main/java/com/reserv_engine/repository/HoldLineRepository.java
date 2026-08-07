package com.reserv_engine.repository;

import com.reserv_engine.entity.HoldLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldLineRepository extends JpaRepository<HoldLine, String> {

}