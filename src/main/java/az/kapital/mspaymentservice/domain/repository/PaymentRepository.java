package az.kapital.mspaymentservice.domain.repository;

import az.kapital.mspaymentservice.domain.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);
}
