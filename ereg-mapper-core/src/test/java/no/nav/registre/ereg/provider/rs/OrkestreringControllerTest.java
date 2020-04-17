package no.nav.registre.ereg.provider.rs;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;

import java.util.Collections;

import no.nav.registre.ereg.TestUtil;
import no.nav.registre.ereg.config.AppConfig;
import no.nav.registre.ereg.service.FlatfileService;
import no.nav.registre.testnorge.domain.dto.eregmapper.v1.EregDTO;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
@ContextConfiguration(classes = {OrkestreringController.class, FlatfileService.class, AppConfig.class})
public class OrkestreringControllerTest {

    private EregDTO data;

    private final String ENV = "t";

    @Mock
    private FlatfileService flatfileService;

    @InjectMocks
    private OrkestreringController orkestreringController;

    private boolean update = false;

    @Before
    public void setUp() {

        data = TestUtil.createDefaultEregData();
    }

    @Test
    public void opprettEnheterIEregOK() {

        when(flatfileService.mapEreg(anyList(), anyBoolean(), anyString())).thenReturn("Ikke viktig så lenge denne ikke er tom");

        ResponseEntity<String> responseEntity = orkestreringController.opprettEnheterIEreg(Collections.singletonList(data), true, ENV);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals("Ikke viktig så lenge denne ikke er tom", responseEntity.getBody());
    }

    @Test
    public void opprettEnheterIEregNotOK() {

        when(flatfileService.mapEreg(anyList(), anyBoolean(), anyString())).thenReturn("");

        ResponseEntity<String> responseEntity = orkestreringController.opprettEnheterIEreg(Collections.singletonList(data), true, ENV);

        assertEquals(HttpStatus.NO_CONTENT, responseEntity.getStatusCode());
    }
}