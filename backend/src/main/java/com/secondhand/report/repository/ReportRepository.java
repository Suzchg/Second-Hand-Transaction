package com.secondhand.report.repository;

import com.secondhand.report.entity.Report;
import com.secondhand.report.entity.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    Page<Report> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);

    /** 用户提交的举报 */
    List<Report> findByReporterIdOrderByCreatedAtDesc(Long reporterId);

    /** 某商品上的举报 */
    List<Report> findByProductIdInOrderByCreatedAtDesc(List<Long> productIds);
}
