package no.nav.registre.ereg.consumer.rs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriTemplate;

import java.net.URI;

@Slf4j
@Component
@RequiredArgsConstructor
public class EregConsumer {

    private final RestTemplate restTemplate;

    @Value("${ereg.api.url}")
    private String eregApiUrl;

    public boolean checkExists(
            String orgnummer,
            String miljoe
    ) {
        try {
            URI uri = new UriTemplate(eregApiUrl + "/v1/organisasjon/{orgnummer}").expand(miljoe, orgnummer);
            ResponseEntity<?> response = restTemplate.getForEntity(uri, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return true;
            }
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
        throw new RuntimeException("Klarer ikke å hente om orgnr " + orgnummer + " eksiterer i  EREG " + miljoe);
    }
}
