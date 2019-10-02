package no.nav.registre.ereg.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

import no.nav.registre.ereg.consumer.rs.JenkinsConsumer;
import no.nav.registre.ereg.mapper.EregMapper;
import no.nav.registre.ereg.provider.rs.request.EregDataRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlatfileService {

    private final EregMapper mapper;
    private final JenkinsConsumer jenkinsConsumer;

    public String mapEreg(List<EregDataRequest> data, boolean sendToEreg, String env, boolean update) {
        String eregData = mapper.mapEregFromRequests(data, update);

        if (sendToEreg) {

            if ("".equals(env)) {
                throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Manglende miljoe variabel");
            }

            if (!sendToJenkins(eregData, env)) {
                return "";
            }
        }

        return eregData;
    }

    public boolean sendToJenkins(String flatFil, String env) {
        return jenkinsConsumer.send(flatFil, env);
    }

}
