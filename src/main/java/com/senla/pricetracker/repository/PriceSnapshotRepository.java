package com.senla.pricetracker.repository;

import com.senla.pricetracker.domain.PricePoint;
import com.senla.pricetracker.domain.PriceSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO price_snapshot (symbol, price, close_time, average_window_minutes, fetched_at)
            VALUES (:symbol, :price, :closeTime, :averageWindowMinutes, :fetchedAt)
            ON CONFLICT (symbol, close_time) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("symbol") String symbol,
                       @Param("price") BigDecimal price,
                       @Param("closeTime") Instant closeTime,
                       @Param("averageWindowMinutes") int averageWindowMinutes,
                       @Param("fetchedAt") Instant fetchedAt);

    Optional<PriceSnapshot> findFirstBySymbolOrderByFetchedAtDescIdDesc(String symbol);

    Page<PriceSnapshot> findBySymbolAndFetchedAtBetween(String symbol, Instant from, Instant to, Pageable pageable);

    @Query(value = """
            SELECT AVG(price)
            FROM price_snapshot
            WHERE symbol = :symbol AND fetched_at BETWEEN :from AND :to
            """, nativeQuery = true)
    BigDecimal findAveragePrice(@Param("symbol") String symbol,
                                @Param("from") Instant from,
                                @Param("to") Instant to);

    @Query("""
            SELECT new com.senla.pricetracker.domain.PricePoint(p.fetchedAt, p.price)
            FROM PriceSnapshot p
            WHERE p.symbol = :symbol AND p.fetchedAt BETWEEN :from AND :to
            ORDER BY p.fetchedAt, p.id
            """)
    List<PricePoint> findPricePoints(@Param("symbol") String symbol,
                                     @Param("from") Instant from,
                                     @Param("to") Instant to);
}
