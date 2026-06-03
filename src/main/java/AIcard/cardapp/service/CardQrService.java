package AIcard.cardapp.service;

import AIcard.cardapp.entity.BusinessCard;
import AIcard.cardapp.entity.CardQr;
import AIcard.cardapp.repository.BusinessCardRepository;
import AIcard.cardapp.repository.CardQrRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Service
public class CardQrService {

    private static final int QR_SIZE = 320;

    private final CardQrRepository cardQrRepository;
    private final BusinessCardRepository businessCardRepository;

    @Value("${app.generated-card-dir:generated-cards}")
    private String generatedCardDir;

    public CardQrService(CardQrRepository cardQrRepository, BusinessCardRepository businessCardRepository) {
        this.cardQrRepository = cardQrRepository;
        this.businessCardRepository = businessCardRepository;
    }

    @Transactional
    public CardQr createOrUpdateQr(Long cardId, String targetUrl) {
        BusinessCard card = businessCardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("명함을 찾을 수 없습니다. cardId=" + cardId));
        return createOrUpdateQr(card, targetUrl);
    }

    @Transactional
    public byte[] readOrCreateOwnedQrImage(Long userId, Long cardId, String targetUrl) {
        BusinessCard card = getOwnedCard(userId, cardId);
        CardQr qr = cardQrRepository.findByCardId(cardId)
                .filter(savedQr -> targetUrl.equals(savedQr.getTargetUrl()) && Files.exists(qrImagePath(cardId)))
                .orElseGet(() -> createOrUpdateQr(card, targetUrl));

        try {
            return Files.readAllBytes(qrImagePath(qr.getCardId()));
        } catch (IOException ex) {
            throw new IllegalStateException("QR 코드 이미지를 읽을 수 없습니다. cardId=" + cardId, ex);
        }
    }

    @Transactional(readOnly = true)
    public String getOwnedPublicUrl(Long userId, Long cardId) {
        return getOwnedCard(userId, cardId).getPublicUrl();
    }

    private CardQr createOrUpdateQr(BusinessCard card, String targetUrl) {
        if (targetUrl == null || targetUrl.isBlank()) {
            throw new IllegalArgumentException("QR 코드 대상 URL이 비어 있습니다.");
        }

        Long cardId = card.getCardId();
        Path qrPath = qrImagePath(cardId);
        writeQrImage(targetUrl, qrPath);

        CardQr qr = cardQrRepository.findByCardId(cardId)
                .orElseGet(CardQr::new);
        qr.setCardId(cardId);
        qr.setTargetUrl(targetUrl);
        qr.setQrImageUrl("/cards/my/" + cardId + "/qr-image");
        return cardQrRepository.save(qr);
    }

    private BusinessCard getOwnedCard(Long userId, Long cardId) {
        if (userId == null) {
            throw new IllegalStateException("로그인 후 이용해주세요.");
        }
        return businessCardRepository.findByCardIdAndUserId(cardId, userId)
                .orElseThrow(() -> new IllegalArgumentException("내 명함을 찾을 수 없습니다. cardId=" + cardId));
    }

    private void writeQrImage(String targetUrl, Path qrPath) {
        try {
            Files.createDirectories(qrPath.getParent());
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.CHARACTER_SET, "UTF-8",
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 1
            );
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(targetUrl, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);
            MatrixToImageWriter.writeToPath(matrix, "PNG", qrPath);
        } catch (Exception ex) {
            throw new IllegalStateException("QR 코드 생성에 실패했습니다.", ex);
        }
    }

    private Path qrImagePath(Long cardId) {
        return Path.of(generatedCardDir, "card_" + cardId, "qr.png").normalize();
    }
}
