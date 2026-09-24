package com.ticketing.repository;

import com.ticketing.entity.Ticket;
import com.ticketing.entity.TicketStatus;
import com.ticketing.entity.ClassificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByClassificationStatusOrderByUpdatedAtDesc(ClassificationStatus classificationStatus);
    List<Ticket> findBySlaDeadlineNotNullAndStatusNotIn(List<TicketStatus> statuses);
    @Query("""
            select t from Ticket t
            where t.user.id = :userId
              and (:status is null or t.status = :status)
              and (:keyword = '' or lower(t.subject) like lower(concat('%', :keyword, '%'))
                   or lower(t.description) like lower(concat('%', :keyword, '%')))
            order by t.createdAt desc
            """)
    List<Ticket> findByOwnerFilters(@Param("userId") Long userId,
                                    @Param("keyword") String keyword,
                                    @Param("status") TicketStatus status);

    @Query("""
            select t from Ticket t
            where t.assignedAgent.id = :agentId
              and (:status is null or t.status = :status)
              and (:keyword = '' or lower(t.subject) like lower(concat('%', :keyword, '%'))
                   or lower(t.description) like lower(concat('%', :keyword, '%')))
            order by t.createdAt desc
            """)
    List<Ticket> findByAgentFilters(@Param("agentId") Long agentId,
                                    @Param("keyword") String keyword,
                                    @Param("status") TicketStatus status);

    @Query("""
            select t from Ticket t
            where (:status is null or t.status = :status)
              and (:keyword = '' or lower(t.subject) like lower(concat('%', :keyword, '%'))
                   or lower(t.description) like lower(concat('%', :keyword, '%')))
            order by t.createdAt desc
            """)
    List<Ticket> findAllByFilters(@Param("keyword") String keyword,
                                  @Param("status") TicketStatus status);
}
