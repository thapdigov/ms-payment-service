package az.kapital.mspaymentservice.service;

import az.kapital.mspaymentservice.client.AccountServiceClient;
import az.kapital.mspaymentservice.client.AuthServiceClient;
import az.kapital.mspaymentservice.domain.entity.PaymentEntity;
import az.kapital.mspaymentservice.domain.repository.PaymentRepository;
import az.kapital.mspaymentservice.model.enums.Currency;
import az.kapital.mspaymentservice.model.enums.PaymentStatus;
import az.kapital.mspaymentservice.model.request.BalanceUpdateRequest;
import az.kapital.mspaymentservice.model.request.TokenValidationRequest;
import az.kapital.mspaymentservice.model.request.TransferRequest;
import az.kapital.mspaymentservice.model.response.AccountResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private AuthServiceClient authServiceClient;

    @Mock
    private AccountServiceClient accountServiceClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private PaymentService paymentService;

    private TransferRequest transferRequest;
    private String authHeader;
    private String token;
    private String username;
    private PaymentEntity existingPaymentSuccess;
    private PaymentEntity existingPaymentPending;
    private PaymentEntity newPayment;
    private PaymentEntity savedSuccessPayment;
    private AccountResponse dummyAccountResponse;

    @BeforeEach
    void setUp() {
        token = "valid-jwt-token";
        authHeader = "Bearer " + token;
        username = "testuser@example.com";
        transferRequest = new TransferRequest(
                "idemp-key",
                1L,
                2L,
                new BigDecimal("100.00"),
                Currency.USD
        );

        existingPaymentSuccess = new PaymentEntity();
        existingPaymentSuccess.setIdempotencyKey("idemp-key");
        existingPaymentSuccess.setStatus(PaymentStatus.SUCCESS);

        existingPaymentPending = new PaymentEntity();
        existingPaymentPending.setIdempotencyKey("idemp-key");
        existingPaymentPending.setStatus(PaymentStatus.PENDING);

        newPayment = new PaymentEntity();
        newPayment.setIdempotencyKey("idemp-key");
        newPayment.setSourceUserId(1L);
        newPayment.setTargetUserId(2L);
        newPayment.setAmount(new BigDecimal("100.00"));
        newPayment.setCurrency(Currency.USD);
        newPayment.setStatus(PaymentStatus.PENDING);

        savedSuccessPayment = new PaymentEntity();
        savedSuccessPayment.setIdempotencyKey("idemp-key");
        savedSuccessPayment.setStatus(PaymentStatus.SUCCESS);

        dummyAccountResponse = new AccountResponse(1L, 1L, Currency.USD, new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("should replay existing successful payment")
    void processTransfer_shouldReplay_WhenExistingSuccess() {
        when(paymentRepository.findByIdempotencyKey("idemp-key")).thenReturn(Optional.of(existingPaymentSuccess));
        when(paymentRepository.save(eq(existingPaymentSuccess))).thenReturn(existingPaymentSuccess);

        PaymentEntity result = paymentService.processTransfer(transferRequest, authHeader);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.IDEMPOTENT_REPLAY);
        verify(paymentRepository, times(1)).save(existingPaymentSuccess);
        verifyNoInteractions(authServiceClient, accountServiceClient, rabbitTemplate);
    }

    @Test
    @DisplayName("should throw exception when existing pending payment")
    void processTransfer_shouldThrow_WhenExistingPending() {
        when(paymentRepository.findByIdempotencyKey("idemp-key")).thenReturn(Optional.of(existingPaymentPending));

        assertThatThrownBy(() -> paymentService.processTransfer(transferRequest, authHeader))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Payment for this key is already pending.");

        verify(paymentRepository, times(1)).findByIdempotencyKey("idemp-key");
        verifyNoMoreInteractions(paymentRepository, authServiceClient, accountServiceClient, rabbitTemplate);
    }

    @Test
    @DisplayName("should fail on general exception")
    void processTransfer_shouldFail_WhenGeneralException() {
        when(paymentRepository.findByIdempotencyKey("idemp-key")).thenReturn(Optional.empty());
        when(authServiceClient.validateToken(any(TokenValidationRequest.class))).thenReturn(username);
        when(paymentRepository.save(argThat(p -> PaymentStatus.PENDING.equals(p.getStatus()))))
                .thenReturn(newPayment);
        when(accountServiceClient.updateBalance(argThat(r -> r.getUserId().equals(1L) &&
                r.getAmount().compareTo(new BigDecimal("-100.00")) == 0)))
                .thenThrow(new RuntimeException("Account not found"));

        assertThatThrownBy(() -> paymentService.processTransfer(transferRequest, authHeader))
                .isInstanceOf(RuntimeException.class)
                .hasMessageStartingWith("Transaction error:Account not found");

        verify(authServiceClient, times(1)).validateToken(any(TokenValidationRequest.class));
        verify(accountServiceClient, times(1)).updateBalance(any(BalanceUpdateRequest.class));
        verifyNoInteractions(rabbitTemplate);
        verify(paymentRepository, times(1)).findByIdempotencyKey("idemp-key");
        verify(paymentRepository, times(1)).
                save(argThat(p -> PaymentStatus.PENDING.equals(p.getStatus())));
        verify(paymentRepository, times(1)).
                save(argThat(p -> PaymentStatus.FAILED.equals(p.getStatus())));
    }

    @Test
    @DisplayName("should throw on invalid authorization header")
    void processTransfer_shouldThrow_WhenInvalidHeader() {
        String invalidHeader = "Invalid";
        when(paymentRepository.findByIdempotencyKey("idemp-key")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.processTransfer(transferRequest, invalidHeader))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("No Authorization header or Bearer token");

        verify(paymentRepository, times(1)).findByIdempotencyKey("idemp-key");
        verifyNoMoreInteractions(paymentRepository);
        verify(authServiceClient, never()).validateToken(any());
        verifyNoInteractions(accountServiceClient, rabbitTemplate);
    }

    @Test
    @DisplayName("should return payment by idempotency key")
    void getPaymentByIdempotencyKey_shouldReturnPayment() {
        when(paymentRepository.findByIdempotencyKey("idemp-key")).thenReturn(Optional.of(newPayment));

        Optional<PaymentEntity> result = paymentService.getPaymentByIdempotencyKey("idemp-key");

        assertThat(result).isPresent();
        assertThat(result.get().getIdempotencyKey()).isEqualTo("idemp-key");
        verify(paymentRepository, times(1)).findByIdempotencyKey("idemp-key");
    }

    @Test
    @DisplayName("should return empty when payment not found")
    void getPaymentByIdempotencyKey_shouldReturnEmpty() {
        when(paymentRepository.findByIdempotencyKey("unknown")).thenReturn(Optional.empty());

        Optional<PaymentEntity> result = paymentService.getPaymentByIdempotencyKey("unknown");

        assertThat(result).isEmpty();
        verify(paymentRepository, times(1)).findByIdempotencyKey("unknown");
    }
}