package org.example.backend.notify;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Calls Twilio's Messages API directly over RestClient (already on the
 * classpath transitively via spring-boot-starter-webmvc) rather than adding
 * the Twilio SDK as a dependency for one POST request.
 */
@Component
public class TwilioSmsGateway implements SmsGateway {

    private final RestClient restClient;
    private final String accountSid;
    private final String authToken;
    private final String fromNumber;

    public TwilioSmsGateway(@Value("${app.twilio-account-sid:}") String accountSid,
                             @Value("${app.twilio-auth-token:}") String authToken,
                             @Value("${app.twilio-from-number:}") String fromNumber,
                             @Value("${app.twilio-base-url:https://api.twilio.com}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
    }

    @Override
    public void send(String toE164, String body) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", toE164);
        form.add("From", fromNumber);
        form.add("Body", body);

        restClient.post()
                .uri("/2010-04-01/Accounts/{sid}/Messages.json", accountSid)
                .headers(headers -> headers.setBasicAuth(accountSid, authToken))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }
}
