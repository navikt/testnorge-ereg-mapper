package no.nav.registre.ereg.mapper;

import com.google.common.base.Strings;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import no.nav.registre.ereg.consumer.rs.EregConsumer;
import no.nav.registre.ereg.csv.NaeringskodeRecord;
import no.nav.registre.ereg.provider.rs.request.*;
import no.nav.registre.ereg.service.NameService;

@Slf4j
@RequiredArgsConstructor
@Component
public class EregMapper {

    private final NameService nameService;
    private final EregConsumer eregConsumer;

    static String getDateNowFormatted() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        return format.format(new Date());
    }

    public String mapEregFromRequests(
            List<EregDataRequest> data,
            String miljoe,
            boolean validate
    ) {

        StringBuilder eregFile = new StringBuilder(makeHeader());
        int units = 0;
        int totalRecords = 0;

        data.stream()
                .filter(d -> d.getNavn() == null)
                .collect(Collectors.groupingBy(EregDataRequest::getEnhetstype))
                .forEach(
                        (enhetstype, requests) -> {
                            List<String> naeringskoder = requests.stream()
                                    .map(d -> {
                                        if (d.getNaeringskode() == null) {
                                            NaeringskodeRecord randomNaeringskode = nameService.getRandomNaeringskode();
                                            String dato = getDateNowFormatted();
                                            if (!"".equals(randomNaeringskode.getValidFrom())) {
                                                dato = randomNaeringskode.getValidFrom().replace("-", "");
                                            }
                                            d.setNaeringskode(Naeringskode.builder()
                                                    .kode(randomNaeringskode.getCode())
                                                    .hjelpeEnhet(false)
                                                    .gyldighetsdato(dato)
                                                    .build());
                                        } else {
                                            if ("".equals(d.getNaeringskode().getKode())) {
                                                throw new HttpClientErrorException(HttpStatus.BAD_REQUEST,
                                                        "Unable to resolve næringskode when the entry was supplied");
                                            }

                                            if ("".equals(d.getNaeringskode().getGyldighetsdato())) {
                                                d.getNaeringskode().setKode(nameService.getNaeringskodeRecord(
                                                        d.getNaeringskode().getKode())
                                                        .getValidFrom().replace("-", ""));
                                            }
                                        }
                                        return d.getNaeringskode();
                                    })
                                    .map(Naeringskode::getKode)
                                    .collect(Collectors.toList());
                            List<String> fullNames = nameService.getFullNames(naeringskoder, enhetstype);
                            for (int i = 0; i < requests.size(); i++) {
                                requests.get(i).setNavn(
                                        NavnRs.builder()
                                                .navneListe(Collections.singletonList(fullNames.get(i)))
                                                .build()
                                );
                            }
                        }
                );

        for (EregDataRequest eregDataRequest : data) {
            if (validate && eregDataRequest.getEndringsType().equals("N")) {
                if (eregConsumer.checkExists(eregDataRequest.getOrgnr(), miljoe)) {
                    log.warn("Hopper over orgnr {} siden det allerede eksisterer i ereg {}", eregDataRequest.getOrgnr(), miljoe);
                    continue;
                }
            } else if (eregDataRequest.getEndringsType().equals("E")) {
                log.info("Utfoerer endring paa orgnr {}", eregDataRequest.getOrgnr());
            }
            RecordsAndCount unit = createUnit(eregDataRequest);
            totalRecords += unit.numRecords;
            eregFile.append(unit.val);
            units++;
        }

        if (units == 0) {
            log.warn("Ingen units aa legge inn i EREG");
            return null;
        }

        eregFile.append(createTrailer(units, totalRecords));

        return eregFile.toString();
    }

    private RecordsAndCount createUnit(EregDataRequest data) {
        int numRecords = 0;
        StringBuilder file;
        if ("E".equals(data.getEndringsType())) {
            file = new StringBuilder(createENH(data.getOrgnr(), data.getEnhetstype(), "E"));
        } else {
            file = new StringBuilder(createENH(data.getOrgnr(), data.getEnhetstype(), "N"));
        }
        String endringsType = "N";
        numRecords++;

        NavnRs navn = data.getNavn();
        assert (navn != null);
        file.append(createNavn(navn.getNavneListe(), navn.getRedNavn() == null ? "" : navn.getRedNavn(), endringsType));
        numRecords++;

        AdresseRs adresse = data.getAdresse();
        if (adresse != null) {
            file.append(createAdresse("PADR", endringsType, adresse.getAdresser(), adresse.getPostnr(), adresse.getLandkode(), adresse.getKommunenr(), adresse.getPoststed()));
            numRecords++;
        }

        AdresseRs forretningsAdresse = data.getForretningsAdresse();
        if (forretningsAdresse != null) {
            file.append(createAdresse("FADR", endringsType, forretningsAdresse.getAdresser(), forretningsAdresse.getPostnr(), forretningsAdresse.getLandkode(), forretningsAdresse.getKommunenr(),
                    forretningsAdresse.getPoststed()));
            numRecords++;
        }

        String epost = data.getEpost();
        if (epost != null) {
            file.append(createSingleFieldWithBase(158, "EPOS", endringsType, epost));
            numRecords++;
        }

        String iAdr = data.getInternetAdresse();
        if (iAdr != null) {
            file.append(createSingleFieldWithBase(158, "IADR", endringsType, iAdr));
            numRecords++;
        }

        Maalform maalform = data.getMaalform();
        if (maalform != null) {
            file.append(createSingleFieldWithBase(8, "MÅL ", endringsType, maalform.getForm()));
            numRecords++;
        }

        Boolean harAnsatte = data.getHarAnsatte();
        if (harAnsatte != null) {
            file.append(createSingleFieldWithBase(8, "ARBG", endringsType, harAnsatte ? "J" : "N"));
            numRecords++;
        }

        String sektorKode = data.getSektorKode();
        if (sektorKode != null) {
            file.append(createSingleFieldWithBase(16, "ISEK", endringsType, sektorKode));
            numRecords++;
        }

        String stiftelsesDato = data.getStiftelsesDato();
        if (stiftelsesDato != null) {
            file.append(createSingleFieldWithBase(16, "STID", endringsType, stiftelsesDato));
            numRecords++;
        }

        Telefon telefon = data.getTelefon();
        if (telefon != null) {
            if (telefon.getFast() != null) {
                file.append(createSingleFieldWithBase(16, "TFON", endringsType, telefon.getFast()));
                numRecords++;
            }
            if (telefon.getFax() != null) {
                file.append(createSingleFieldWithBase(16, "TFAX", endringsType, telefon.getFax()));
                numRecords++;
            }
            if (telefon.getMobil() != null) {
                file.append(createSingleFieldWithBase(16, "MTLF", endringsType, telefon.getMobil()));
                numRecords++;
            }
        }

        String frivillighetsKode = data.getFrivillighetsKode();
        if (frivillighetsKode != null) {
            file.append(createFrivilligKategori(frivillighetsKode, "1", endringsType));
            numRecords++;
        }

        String nedleggelse = data.getNedleggelsesDato();
        if (nedleggelse != null) {
            file.append(createDatoRecord("NDAT", nedleggelse, endringsType));
            numRecords++;
        }
        String oppstart = data.getOppstartsDato();
        if (oppstart != null) {
            file.append(createDatoRecord("BDAT", oppstart, endringsType));
            numRecords++;
        }
        String eierskapskifte = data.getEierskapskifteDato();
        if (eierskapskifte != null) {
            file.append(createDatoRecord("EDAT", eierskapskifte, endringsType));
            numRecords++;
        }

        Boolean kjoensfordeling = data.getKjoensfordeling();
        if (kjoensfordeling != null) {
            file.append(createSingleFieldWithBase(9, "KJRPN", endringsType, kjoensfordeling ? "J" : "N"));
            numRecords++;
        }

        Boolean utelukkendeVirksomhetINorge = data.getUtelukkendeVirksomhetINorge();
        if (utelukkendeVirksomhetINorge != null) {
            file.append(createSingleFieldWithBase(9, "UVNON", endringsType, utelukkendeVirksomhetINorge ? "J" : "N"));
            numRecords++;
        }

        Boolean heleidINorge = data.getUtelukkendeVirksomhetINorge();
        if (heleidINorge != null) {
            file.append(createSingleFieldWithBase(9, "UENON", endringsType, heleidINorge ? "J" : "N"));
            numRecords++;
        }

        Boolean fravalgAvRevisjonen = data.getUtelukkendeVirksomhetINorge();
        if (fravalgAvRevisjonen != null) {
            file.append(createSingleFieldWithBase(9, "RVFGN", endringsType, fravalgAvRevisjonen ? "J" : "N"));
            numRecords++;
        }

        UtenlandsRegister utenlandsRegister = data.getUtenlandsRegister();
        if (utenlandsRegister != null) {
            file.append(createHjemlandsRegister(utenlandsRegister.getNavn(), utenlandsRegister.getRegisterNr(), utenlandsRegister.getAdresse(), endringsType));
            numRecords++;
        }

        Map<String, String> statuser = data.getStatuser();
        if (statuser != null) {
            for (Map.Entry<String, String> entry : statuser.entrySet()) {
                file.append(createStatus(entry.getKey(), entry.getValue()));
                numRecords++;
            }
        }

        UnderlagtHjemland underlagtHjemland = data.getUnderlagtHjemland();
        if (underlagtHjemland != null) {
            file.append(createUnderlagtHjemlandsLovgivning(underlagtHjemland.getUnderlagtLovgivningLandkoode(),
                    underlagtHjemland.getForetaksformHjemland(),
                    underlagtHjemland.getBeskrivelseHjemland(),
                    underlagtHjemland.getBeskrivelseNorge(), endringsType));
            numRecords++;
        }

        Kapital kapital = data.getKapital();
        if (kapital != null) {
            final int RECORD_SIZE = 70;
            int size = kapital.getFritekst().length();
            if (size > RECORD_SIZE * 4) {
                throw new IllegalArgumentException("For mange tegn i kapital fritekst. Kan max være 4 records med RECORD_SIZE tegn. Den har størrelse på " + size);
            }
            for (int i = 0; i < Math.ceil((double) size / (double) RECORD_SIZE); i++) {
                int readStopIndex = (i + 1) * RECORD_SIZE;
                if (size < readStopIndex) {
                    readStopIndex = size;
                }
                file.append(createKapitalRecord(kapital.getValuttakode(), kapital.getKapital(), kapital.getKapitalInnbetalt(),
                        kapital.getKapitalBundet(), kapital.getFritekst().substring(i * RECORD_SIZE, readStopIndex), endringsType));
                numRecords++;
            }
        }

        Naeringskode naeringskode = data.getNaeringskode();
        if (naeringskode != null) {
            file.append(createNaeringskodeRecord(naeringskode.getKode(), naeringskode.getGyldighetsdato(),
                    naeringskode.getHjelpeEnhet(), endringsType));
            numRecords++;
        }

        String formaal = data.getFormaal();
        if (formaal != null) {
            final int RECORD_SIZE = 70;
            int size = formaal.length();
            for (int i = 0; i < Math.ceil((double) size / (double) RECORD_SIZE); i++) {
                int readStopIndex = (i + 1) * RECORD_SIZE;
                if (size < readStopIndex) {
                    readStopIndex = size;
                }
                file.append(createSingleFieldWithBase(78, "FORM", endringsType, formaal.substring(i * RECORD_SIZE, readStopIndex)));
                numRecords++;
            }
        }

        List<String> frivilligMVA = data.getFrivilligRegistreringerMVA();
        if (frivilligMVA != null) {
            if (frivilligMVA.size() > 5) {
                throw new IllegalStateException("For mange frivillige mva registreringer for et dokument på en bedrift");
            }
            List<String> collect = frivilligMVA.stream().map(f -> createSingleFieldWithBase(14, "FMVA", endringsType, f)).collect(Collectors.toList());
            for (String s : collect) {
                file.append(s);
                numRecords++;
            }
        }

        List<KnytningRs> knytninger = data.getKnytninger();
        if (knytninger != null) {
            List<String> collect = knytninger.stream().map(k -> createKnyntningsRecord(
                    k.getType(),
                    k.getAnsvarsandel(),
                    k.getFratreden(),
                    k.getOrgnr(),
                    k.getValgtAv(),
                    k.getKorrektOrgNr()))
                    .collect(Collectors.toList());
            for (String s : collect) {
                file.append(s);
                numRecords++;
            }
        }

        return new RecordsAndCount(file.toString(), numRecords);
    }

    private String createENH(
            String orgId,
            String unitType,
            String endringsType
    ) {
        StringBuilder stringBuilder = createStringBuilderWithReplacement(49, ' ');

        String dateNowFormatted = getDateNowFormatted();

        String undersakstype = endringsType.equals("E") ? "EN" : "NY";

        stringBuilder.replace(0, "ENH".length(), "ENH")
                .replace(4, 4 + orgId.length(), orgId)
                .replace(13, 13 + unitType.length(), unitType)
                .replace(17, 18, endringsType)
                .replace(18, 18 + undersakstype.length(), undersakstype)
                .replace(22, 22 + (dateNowFormatted + dateNowFormatted).length(), dateNowFormatted + dateNowFormatted + "J")
                .append("\n");

        return stringBuilder.toString();
    }

    private String createNavn(
            List<String> navneListe,
            String redigertNavn,
            String endringsType
    ) {
        StringBuilder stringBuilder = createBaseStringbuilder(219, "NAVN", endringsType);
        concatListToString(stringBuilder, navneListe, 8);
        stringBuilder.replace(183, 183 + redigertNavn.length(), redigertNavn).append("\n");
        return stringBuilder.toString();
    }

    private String makeHeader() {
        return "HEADER " + getDateNowFormatted() + "00000" + "AA A\n";
    }

    private String createFrivilligKategori(
            String kode,
            String rangering,
            String endringsType
    ) {

        StringBuilder stringBuilder = createBaseStringbuilder(14, "KATG", endringsType);
        stringBuilder.replace(8, 8 + kode.length(), kode)
                .replace(13, 14, rangering)
                .append("\n");
        return stringBuilder.toString();
    }

    private String createDatoRecord(
            String type,
            String dato,
            String endringsType
    ) {

        StringBuilder stringBuilder = createBaseStringbuilder(16, type, endringsType);
        stringBuilder.replace(8, 8 + dato.length(), dato)
                .append("\n");
        return stringBuilder.toString();
    }

    private String createAdresse(
            String type,
            String endringsType,
            List<String> addresses,
            String postNr,
            String landCode,
            String kommuneNr,
            String postSted
    ) {
        StringBuilder stringBuilder = createBaseStringbuilder(185, type, endringsType);
        stringBuilder.replace(8, 8 + postNr.length(), postNr)
                .replace(17, 17 + landCode.length(), landCode)
                .replace(20, 20 + getStringLength(kommuneNr), Strings.nullToEmpty(kommuneNr))
                .replace(30, 30 + getStringLength(postSted), Strings.nullToEmpty(postSted));
        if (addresses.size() > 3) {
            log.warn("Antall addresser er for mange, bruker de 3 første");
        }

        concatListToString(stringBuilder, addresses, 64);
        stringBuilder.append("\n");
        return stringBuilder.toString();
    }

    private String createUnderlagtHjemlandsLovgivning(
            String landkode,
            String foretaksform,
            String beskrivelseHjemland,
            String beskrivelseNorge,
            String endringsType
    ) {

        StringBuilder stringBuilder = createBaseStringbuilder(159, "ULOV", endringsType);
        stringBuilder.replace(8, 8 + landkode.length(), landkode)
                .replace(11, 11 + foretaksform.length(), foretaksform)
                .replace(19, 19 + beskrivelseHjemland.length(), beskrivelseHjemland)
                .replace(89, 89 + beskrivelseNorge.length(), beskrivelseNorge)
                .append("\n");
        return stringBuilder.toString();
    }

    private String createKapitalRecord(
            String valuttakode,
            String kapital,
            String kapitalInnbetalt,
            String kapitalBundet,
            String fritekst,
            String endringsType
    ) {
        StringBuilder stringBuilder = createBaseStringbuilder(187, "KAPI", endringsType);
        stringBuilder.replace(8, 8 + valuttakode.length(), valuttakode)
                .replace(11, 29, createStringBuilderWithReplacement(18, '0').toString())
                .replace(29 - kapital.length(), 29, kapital)
                .replace(29, 47, createStringBuilderWithReplacement(18, '0').toString())
                .replace(47 - kapitalInnbetalt.length(), 47, kapitalInnbetalt)
                .replace(47, 47 + kapitalBundet.length(), kapitalBundet)
                .replace(117, 117 + fritekst.length(), fritekst)
                .append("\n");
        return stringBuilder.toString();
    }

    private String createNaeringskodeRecord(
            String naeringskode,
            String gyldighetsDato,
            Boolean hjelpeEnhet,
            String endringsType
    ) {
        String verdi = hjelpeEnhet ? "J" : endringsType;
        StringBuilder stringBuilder = createBaseStringbuilder(23, "NACE", endringsType);
        stringBuilder.replace(8, 8 + naeringskode.length(), naeringskode)
                .replace(14, 14 + gyldighetsDato.length(), gyldighetsDato)
                .replace(22, 22 + verdi.length(), verdi)
                .append("\n");
        return stringBuilder.toString();
    }

    private StringBuilder createBaseStringbuilder(
            int size,
            String type,
            String endringsType
    ) {
        StringBuilder stringBuilder = createStringBuilderWithReplacement(size, ' ');
        stringBuilder.replace(0, type.length(), type)
                .replace(4, 5, endringsType);
        return stringBuilder;
    }

    private String createSingleFieldWithBase(
            int size,
            String type,
            String endringsType,
            String value
    ) {
        StringBuilder stringBuilder = createBaseStringbuilder(size, type, endringsType);
        stringBuilder.replace(8, 8 + value.length(), value)
                .append("\n");
        return stringBuilder.toString();
    }

    private void concatListToString(
            StringBuilder stringBuilder,
            List<String> list,
            int indexStart
    ) {
        int iterSize = 3;
        if (list.size() < iterSize) {
            iterSize = list.size();
        }

        int start = indexStart;
        for (int i = 0; i < iterSize; i++) {
            stringBuilder.replace(start, start + list.get(i).length(), list.get(i));
            start = start + 35 * (i + 1);
        }
    }

    private String createStatus(
            String statusType,
            String endringsType
    ) {
        StringBuilder stringBuilder = createStringBuilderWithReplacement(8, ' ');
        stringBuilder.replace(0, statusType.length(), statusType)
                .replace(4, 4 + endringsType.length(), endringsType).append("\n");
        return stringBuilder.toString();
    }

    private String createTrailer(
            int units,
            int records
    ) {
        //Legger til header og trailer i antall records
        records = records + 2;
        StringBuilder stringBuilder = createStringBuilderWithReplacement(23, '0');
        stringBuilder.replace(0, 6, "TRAIER ")
                .replace(14 - String.valueOf(units).length(), 14, String.valueOf(units))
                .replace(23 - String.valueOf(records).length(), 24, String.valueOf(records))
                .append("\n");
        return stringBuilder.toString();
    }

    private StringBuilder createStringBuilderWithReplacement(
            int size,
            char replacement
    ) {
        StringBuilder stringBuilder = new StringBuilder(size);
        stringBuilder.setLength(size);
        for (int i = 0; i < stringBuilder.length(); i++) {
            stringBuilder.setCharAt(i, replacement);
        }
        return stringBuilder;
    }

    private String createHjemlandsRegister(
            List<String> navn,
            String registerNr,
            AdresseRs adresse,
            String endringsType
    ) {

        StringBuilder stringBuilder = createBaseStringbuilder(291, "UREG", endringsType);
        stringBuilder.replace(8, 8 + registerNr.length(), registerNr);

        concatListToString(stringBuilder, navn, 43);

        stringBuilder.replace(148, 148 + adresse.getLandkode().length(), adresse.getLandkode())
                .replace(152, 152 + adresse.getPoststed().length(), adresse.getPoststed());

        List<String> addresses = adresse.getAdresser();

        concatListToString(stringBuilder, addresses, 186);
        stringBuilder.append("\n");
        return stringBuilder.toString();
    }

    private String createKnyntningsRecord(
            String type,
            String ansvarsandel,
            String fratreden,
            String orgNr,
            String valgtAv,
            String korrektOrgNr
    ) {
        StringBuilder stringBuilder = createStringBuilderWithReplacement(66, ' ');
        stringBuilder
                .replace(0, 8, type)
                .replace(8, 9, "K")
                .replace(9, 10, "D")
                .replace(10, 10 + getStringLength(ansvarsandel), Strings.nullToEmpty(ansvarsandel))
                .replace(40, 41, Strings.nullToEmpty(fratreden))
                .replace(41, 41 + getStringLength(orgNr), Strings.nullToEmpty(orgNr))
                .replace(50, 50 + getStringLength(valgtAv), Strings.nullToEmpty(valgtAv))
                .replace(57, 57 + getStringLength(korrektOrgNr), Strings.nullToEmpty(korrektOrgNr))
                .append("\n");
        return stringBuilder.toString();
    }

    private int getStringLength(String value) {
        return value == null ? 0 : value.length();
    }

    @AllArgsConstructor
    private class RecordsAndCount {

        public String val;
        public int numRecords;
    }

}
