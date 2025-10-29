package az.kapital.mspaymentservice.controller;

import az.kapital.mspaymentservice.domain.entity.PaymentEntity;
import az.kapital.mspaymentservice.domain.repository.PaymentRepository;
import az.kapital.mspaymentservice.model.request.TransferRequest;
import az.kapital.mspaymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/transfer")
    public ResponseEntity<PaymentEntity> transferMoney(
            @RequestBody @Valid TransferRequest request,
            @RequestHeader("Authorization") String authorizationHeader) {

        PaymentEntity result = paymentService.processTransfer(request, authorizationHeader);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{idempotencyKey}/status")
    public ResponseEntity<PaymentEntity> getPaymentStatus(@PathVariable String idempotencyKey) {
        return paymentService.getPaymentByIdempotencyKey(idempotencyKey)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
