package az.kapital.mspaymentservice.service;

import az.kapital.mspaymentservice.client.AccountServiceClient;
import az.kapital.mspaymentservice.client.AuthServiceClient;
import az.kapital.mspaymentservice.domain.entity.PaymentEntity;
import az.kapital.mspaymentservice.domain.repository.PaymentRepository;
import az.kapital.mspaymentservice.exception.InsufficientFundsException;
import az.kapital.mspaymentservice.model.request.BalanceUpdateRequest;
import az.kapital.mspaymentservice.model.enums.PaymentStatus;
import az.kapital.mspaymentservice.model.request.RiskCheckDTO;
import az.kapital.mspaymentservice.model.request.TokenValidationRequest;
import az.kapital.mspaymentservice.model.request.TransferRequest;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static az.kapital.mspaymentservice.config.RabbitMQConfig.RISK_EXCHANGE;
import static az.kapital.mspaymentservice.config.RabbitMQConfig.RISK_ROUTING_KEY;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AuthServiceClient authServiceClient;
    private final AccountServiceClient accountServiceClient;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public PaymentEntity processTransfer(TransferRequest request, String authorizationHeader) {

        Optional<PaymentEntity> existingPayment = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey());

        if (existingPayment.isPresent()) {
            PaymentEntity payment = existingPayment.get();

            if (PaymentStatus.SUCCESS.equals(payment.getStatus())) {
                payment.setStatus(PaymentStatus.IDEMPOTENT_REPLAY);
                return paymentRepository.save(payment);
            } else if (PaymentStatus.PENDING.equals(payment.getStatus())) {
                throw new RuntimeException("Payment for this key is already pending.");
            }
        }

        String authenticatedUsername = authServiceClient.validateToken(new TokenValidationRequest(extractToken(authorizationHeader)));

        PaymentEntity newPayment = new PaymentEntity();
        newPayment.setIdempotencyKey(request.getIdempotencyKey());
        newPayment.setSourceUserId(request.getSourceUserId());
        newPayment.setTargetUserId(request.getDestinationAccountId());
        newPayment.setAmount(request.getAmount());
        newPayment.setCurrency(request.getCurrency());
        newPayment.setStatus(PaymentStatus.PENDING);

        newPayment = paymentRepository.save(newPayment);

        try {
            accountServiceClient.updateBalance(new BalanceUpdateRequest(
                    request.getSourceUserId(),
                    request.getCurrency(),
                    request.getAmount().negate()
            ));

            accountServiceClient.updateBalance(new BalanceUpdateRequest(
                    request.getDestinationAccountId(),
                    request.getCurrency(),
                    request.getAmount()
            ));

            newPayment.setStatus(PaymentStatus.SUCCESS);
            PaymentEntity finalPayment = paymentRepository.save(newPayment);

            sendRiskCheckMessage(finalPayment);

            return finalPayment;

        } catch (FeignException.BadRequest e) {
            String errorMsg = e.getMessage();
            if (errorMsg.contains("InsufficientFunds")) {
                newPayment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(newPayment);
                throw new InsufficientFundsException("Insufficient balance: " + errorMsg);
            }
        } catch (Exception e) {
            newPayment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(newPayment);
            throw new RuntimeException("Transaction error:" + e.getMessage());
        }
        return newPayment;
    }

    public Optional<PaymentEntity> getPaymentByIdempotencyKey(String idempotencyKey) {
        return paymentRepository.findByIdempotencyKey(idempotencyKey);
    }

    private void sendRiskCheckMessage(PaymentEntity payment) {
        RiskCheckDTO riskDTO = new RiskCheckDTO(
                payment.getId(),
                payment.getSourceUserId(),
                payment.getAmount(),
                payment.getCurrency()
        );
        rabbitTemplate.convertAndSend(RISK_EXCHANGE, RISK_ROUTING_KEY, riskDTO);
    }

    private String extractToken(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }
        throw new RuntimeException("No Authorization header or Bearer token");
    }
}
