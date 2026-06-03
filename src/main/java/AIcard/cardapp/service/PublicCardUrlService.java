package AIcard.cardapp.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Service
public class PublicCardUrlService {

    @Value("${app.public-base-url:}")
    private String publicBaseUrl;

    public String buildPublicCardUrl(HttpServletRequest request, String publicUrl) {
        String baseUrl = normalizeBaseUrl(publicBaseUrl);
        if (!baseUrl.isBlank()) {
            return baseUrl + "/public/card/" + publicUrl;
        }

        return ServletUriComponentsBuilder.fromRequest(request)
                .replacePath(request.getContextPath() + "/public/card/" + publicUrl)
                .replaceQuery(null)
                .build()
                .toUriString();
    }

    private String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
