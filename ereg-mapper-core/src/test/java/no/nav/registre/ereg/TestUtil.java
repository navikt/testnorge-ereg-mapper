package no.nav.registre.ereg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import no.nav.registre.testnorge.domain.dto.eregmapper.v1.AdresseDTO;
import no.nav.registre.testnorge.domain.dto.eregmapper.v1.EregDTO;
import no.nav.registre.testnorge.domain.dto.eregmapper.v1.KapitalDTO;
import no.nav.registre.testnorge.domain.dto.eregmapper.v1.Maalform;
import no.nav.registre.testnorge.domain.dto.eregmapper.v1.NaeringskodeDTO;
import no.nav.registre.testnorge.domain.dto.eregmapper.v1.NavnDTO;
import no.nav.registre.testnorge.domain.dto.eregmapper.v1.TelefonDTO;
import no.nav.registre.testnorge.domain.dto.eregmapper.v1.UnderlagtHjemlandDTO;
import no.nav.registre.testnorge.domain.dto.eregmapper.v1.UtenlandsRegisterDTO;

public class TestUtil {
    public static EregDTO createDefaultEregData() {

        ArrayList<String> navneListe = new ArrayList<>();
        navneListe.add("Flott");
        navneListe.add("bedrift");

        ArrayList<String> adresser = new ArrayList<>();
        adresser.add("S2");

        Map<String, String> statuser = new HashMap<>();
        statuser.put("IPF", "N");

        return EregDTO.builder()
                .orgnr("123")
                .navn(NavnDTO.builder()
                        .navneListe(navneListe)
                        .redNavn("Tull")
                        .build())
                .enhetstype("BEDR")
                .endringsType("N")
                .adresse(AdresseDTO.builder()
                        .adresser(adresser)
                        .kommunenr("1")
                        .landkode("NOR")
                        .postnr("0175")
                        .poststed("OSLO")
                        .build())
                .forretningsAdresse(AdresseDTO.builder()
                        .adresser(adresser)
                        .kommunenr("1")
                        .landkode("NOR")
                        .postnr("0175")
                        .poststed("OSLO")
                        .build())
                .epost("noreply@nav.no")
                .internetAdresse("https://www.ikkenav.no")
                .frivilligRegistreringerMVA(Collections.singletonList("NOE!"))
                .harAnsatte(true)
                .sektorKode("AB")
                .stiftelsesDato("18062019")
                .telefon(TelefonDTO.builder()
                        .fast("11111111")
                        .fax("22222222")
                        .mobil("33333333")
                        .build())
                .frivillighetsKode("DA")
                .nedleggelsesDato("18062019")
                .eierskapskifteDato("18062019")
                .oppstartsDato("18062019")
                .maalform(Maalform.B)
                .utelukkendeVirksomhetINorge(true)
                .heleidINorge(true)
                .fravalgAvRevisjonen(false)
                .utenlandsRegister(UtenlandsRegisterDTO.builder()
                        .adresse(AdresseDTO.builder()
                                .adresser(adresser)
                                .kommunenr("1")
                                .landkode("NOR")
                                .postnr("0175")
                                .poststed("OSLO")
                                .build())
                        .navn(Collections.singletonList("Utenlands navn!"))
                        .registerNr("098")
                        .build())
                .statuser(statuser)
                .kjoensfordeling(true)
                .underlagtHjemland(UnderlagtHjemlandDTO.builder()
                        .beskrivelseHjemland("Noe som ikke er mer enn 70 tegn")
                        .beskrivelseNorge("Enda mer under 70")
                        .foretaksformHjemland("AS")
                        .underlagtLovgivningLandkoode("J")
                        .build())
                .kapital(KapitalDTO.builder()
                        .valuttakode("2?")
                        .kapital("111")
                        .kapitalBundet("333")
                        .kapitalInnbetalt("222")
                        .fritekst("Noe som kan gå over flere records........................... Minst 70 tegn for å få delt opp records i flere")
                        .build())
                .naeringskode(NaeringskodeDTO.builder()
                        .gyldighetsdato("18062019")
                        .hjelpeEnhet(false)
                        .kode("0?")
                        .build())
                .formaal("Jobb")
                .build();
    }
}
