package sg.edu.nus.facilityflow.ai.provider;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Versioned, deterministic Work Plan safety policy (AI-SEC-012).
 *
 * <p>This policy is deliberately conservative: a dangerous instruction is rejected unless it is
 * directly prohibited, and conditional authorization still rejects the complete result. It is not
 * a professional safety certification or an LLM safety judge.
 */
final class WorkPlanSafetyPolicy {
    static final String VERSION = "work-plan-safety-v2";

    private static final Pattern DANGEROUS_ACTION = Pattern.compile(
            "\\b(?:work(?:ing)?\\s+(?:on|with)\\s+(?:an?\\s+)?(?:live|energized|electrified)|"
                    + "bypass(?:ing)?\\s+(?:(?:the|a|an)\\s+)?(?:safety\\s+)?(?:lockout|tagout|interlock)s?|"
                    + "(?:remove|disable|defeat|ignore)\\s+(?:(?:the|a|an)\\s+)?(?:safety\\s+)?(?:guard|interlock|lockout|tagout)s?|"
                    + "(?:continue|proceed|enter|handle)\\w*\\s+(?:during|into|an?\\s+)?"
                    + "(?:a\\s+)?(?:suspected\\s+)?(?:gas\\s+leak|fire|structural\\s+instability|"
                    + "confined\\s+space|unknown\\s+hazardous\\s+material)|"
                    + "without\\s+(?:the\\s+)?(?:required\\s+)?(?:ppe|personal\\s+protective\\s+equipment|protective\\s+equipment)|"
                    + "trabaj(?:ar|ando)\\s+(?:con|en)\\s+(?:un\\s+)?(?:equipo\\s+)?(?:vivo|energizado)|"
                    + "puent(?:ear|eando)\\s+(?:(?:el|la)\\s+)?(?:interbloqueo|bloqueo\\s+de\\s+seguridad)|"
                    + "desactivar\\s+(?:(?:el|la)\\s+)?(?:proteccion|interbloqueo|bloqueo)|"
                    + "entrar\\s+(?:en\\s+)?(?:un\\s+)?espacio\\s+confinado|"
                    + "(?:fuga\\s+de\\s+gas|incendio|sin\\s+(?:el\\s+)?equipo\\s+de\\s+proteccion)|"
                    + "bekerja\\s+(?:pada|dengan)\\s+(?:peralatan\\s+)?(?:berarus|bertenaga)|"
                    + "pintas(?:kan)?\\s+(?:(?:interlock|saling\\s+kunci)|pengadang\\s+keselamatan)|"
                    + "matikan\\s+(?:pengadang\\s+keselamatan|interlock)|"
                    + "masuk\\s+(?:ke\\s+)?ruang\\s+terkurung|"
                    + "(?:kebocoran\\s+gas|kebakaran|tanpa\\s+(?:ppe|alat\\s+pelindung)))\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /* Compact matching catches separators and common leetspeak inserted between letters. */
    private static final Pattern COMPACT_DANGEROUS = Pattern.compile(
            "(?:work(?:ing)?on(?:an?)?(?:live|energized|electrified)|"
                    + "bypass(?:ing)?(?:the)?(?:safety)?(?:lockout|tagout|interlocks?)|"
                    + "(?:remove|disable|defeat|ignore)(?:thesafety)?(?:guard|interlock|lockout|tagout)s?|"
                    + "(?:continue|proceed|enter|handle)(?:during|into|an?)?(?:suspected)?"
                    + "(?:gasleak|fire|structuralinstability|confinedspace|unknownhazardousmaterial)|"
                    + "without(?:therequired)?(?:ppe|personalprotectiveequipment|protectiveequipment)|"
                    + "trabajar(?:con|en)(?:unequipo)?(?:vivo|energizado)|"
                    + "puentear(?:el|la)?(?:interbloqueo|bloqueodeseguridad)|"
                    + "desactivar(?:el|la)?(?:proteccion|interbloqueo|bloqueo)|"
                    + "entrar(?:en)?(?:un)?espacioconfinado|"
                    + "(?:fugadegas|incendio|sinequipodeproteccion)|"
                    + "bekerja(?:pada|dengan)(?:peralatan)?(?:berarus|bertenaga)|"
                    + "pintaskan?(?:interlock|salingkunci|pengadangkeselamatan)|"
                    + "matikan(?:pengadangkeselamatan|interlock)|"
                    + "masuk(?:ke)?ruangterkurung|"
                    + "(?:kebocorangas|kebakaran|tanpappe|tanpaalatpelindung))");

    private static final Pattern DIRECT_NEGATION = Pattern.compile(
            "(?:do\\s+not|don't|never|avoid|no\\s+se\\s+debe|no\\s+debes?|nunca|jangan|dilarang)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern COMPACT_NEGATION = Pattern.compile(
            "(?:donot|never|avoid|nosede?be|nodebes?|nunca|jangan|dilarang)$");
    private static final Pattern CONDITIONAL_AUTHORIZATION = Pattern.compile(
            "(?:unless|except|provided|authorized|authori[sz]ation|supervisor|approval|permit|"
                    + "salvo\\s+autorizacion|si\\s+esta\\s+autorizado|permiso|supervisor|"
                    + "kecuali|dibenarkan|kebenaran|penyelia)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern STOP_GUIDANCE = Pattern.compile(
            "(?:stop|escalat(?:e|ion|ing|ed)?|seek\\s+(?:help|assistance)|"
                    + "deten(?:ga|er|erse)|escal(?:ar|e)|evacuar|hentikan|berhenti|laporkan)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern STOP_HAZARD = Pattern.compile(
            "(?:live|energized|electrified|gas\\s+leak|fire|structural|confined|hazardous|"
                    + "lockout|tagout|interlock|ppe|protective\\s+equipment|unknown|chemical|injur|emergency|"
                    + "vivo|energizado|fuga\\s+de\\s+gas|incendio|estructural|confinado|peligroso|"
                    + "interbloqueo|proteccion|berarus|kebocoran|kebakaran|terkurung|bahaya)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private WorkPlanSafetyPolicy() {}

    static boolean hasStopEscalation(List<String> values) {
        return values.stream().map(WorkPlanSafetyPolicy::normalize)
                .anyMatch(value -> STOP_GUIDANCE.matcher(value).find() && STOP_HAZARD.matcher(value).find());
    }

    static boolean rejects(List<String> values) {
        for (String value : values) {
            String normalized = normalize(value);
            for (String clause : clauses(normalized)) {
                var dangerous = DANGEROUS_ACTION.matcher(clause);
                while (dangerous.find()) {
                    String prefix = clause.substring(0, dangerous.start()).trim();
                    if (!DIRECT_NEGATION.matcher(prefix).find()
                            || CONDITIONAL_AUTHORIZATION.matcher(clause).find()) {
                        return true;
                    }
                }
                String compactClause = compact(clause);
                var compactDangerous = COMPACT_DANGEROUS.matcher(compactClause);
                while (compactDangerous.find()) {
                    String prefix = compactClause.substring(0, compactDangerous.start());
                    if (!COMPACT_NEGATION.matcher(prefix).find()
                            || CONDITIONAL_AUTHORIZATION.matcher(clause).find()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String normalize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('\u0000', ' ')
                .replace('0', 'o')
                .replace('1', 'i')
                .replace('3', 'e')
                .replace('4', 'a')
                .replace('5', 's')
                .replace('7', 't')
                .replace('@', 'a')
                .replace('$', 's');
        return normalized.replaceAll("\\p{Cf}", "");
    }

    private static String compact(String normalized) {
        return normalized.replaceAll("[^\\p{L}\\p{Nd}]", "");
    }

    private static String[] clauses(String normalized) {
        return normalized.split("[.!?;:\\n]+");
    }
}
