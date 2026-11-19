package ma.ensat.hirelink.repository;

import ma.ensat.hirelink.model.SentEmail;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SentEmailRepository extends JpaRepository<SentEmail, Long> {

    List<SentEmail> findAllByOrderByCreatedAtDescIdDesc(Pageable page);

    /** Journal des e-mails filtré côté serveur : période [from, to[, statut, type, destinataire ou objet. */
    @Query("""
            select e from SentEmail e
            where (:fromAt is null or e.createdAt >= :fromAt)
              and (:toAt is null or e.createdAt < :toAt)
              and (:status is null or e.status = :status)
              and (:kind is null or e.kind = :kind)
              and (:q is null or lower(e.recipient) like :q or lower(e.subject) like :q)
            order by e.createdAt desc, e.id desc
            """)
    org.springframework.data.domain.Page<SentEmail> search(
            @Param("fromAt") Instant fromAt,
            @Param("toAt") Instant toAt,
            @Param("status") SentEmail.Status status,
            @Param("kind") String kind,
            @Param("q") String q,
            Pageable pageable);

    @Query("select distinct e.kind from SentEmail e where e.kind is not null order by e.kind")
    List<String> distinctKinds();

    long countByStatusAndCreatedAtAfter(SentEmail.Status status, Instant after);

    @Modifying
    @Query("DELETE FROM SentEmail e WHERE e.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
