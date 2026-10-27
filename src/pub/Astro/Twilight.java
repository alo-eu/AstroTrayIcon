package pub.Astro;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Berechnet Sonnenauf-/-untergang sowie buergerliche, nautische und
 * astronomische Daemmerung fuer ein Datum und einen Ort.
 *
 * <p>Grundlage ist der Algorithmus des NOAA Solar Calculator
 * (vereinfachte Sonnenbahn nach Jean Meeus, "Astronomical Algorithms").
 * Die Genauigkeit liegt bei etwa einer Minute, fuer Breiten jenseits von
 * +/- 72 Grad kann der Fehler groesser werden.
 *
 * <p>Benoetigt Java 8 oder neuer (java.time).
 *
 * <p>Beispiel:
 * <pre>
 *   Twilight.Result r = Twilight.compute(LocalDate.of(2026, 6, 21),
 *                                        52.52, 13.405, ZoneId.of("Europe/Berlin"));
 *   r.get(Twilight.Kind.CIVIL).dawn();   // Beginn der buergerlichen Daemmerung
 *   r.get(Twilight.Kind.CIVIL).dusk();   // Ende der buergerlichen Daemmerung
 * </pre>
 */
public final class Twilight {

    /** Art des Ereignisses, definiert ueber die Hoehe des Sonnenmittelpunkts. */
    public enum Kind {
        /** Sonnenaufgang / -untergang (Oberrand am Horizont, inkl. Refraktion). */
        SUNRISE_SUNSET(-0.833, "Sonnenauf-/-untergang"),
        /** Buergerliche Daemmerung: Sonne 6 Grad unter dem Horizont. */
        CIVIL(-6.0, "Buergerliche Daemmerung"),
        /** Nautische Daemmerung: Sonne 12 Grad unter dem Horizont. */
        NAUTICAL(-12.0, "Nautische Daemmerung"),
        /** Astronomische Daemmerung: Sonne 18 Grad unter dem Horizont. */
        ASTRONOMICAL(-18.0, "Astronomische Daemmerung");

        private final double altitudeDeg;
        private final String label;

        Kind(double altitudeDeg, String label) {
            this.altitudeDeg = altitudeDeg;
            this.label = label;
        }

        /** Sonnenhoehe in Grad, bei der das Ereignis eintritt. */
        public double altitudeDeg() {
            return altitudeDeg;
        }

        public String label() {
            return label;
        }
    }

    /** Zustand eines Ereignisses an diesem Tag. */
    public enum State {
        /** Die Sonne durchlaeuft die Hoehe morgens und abends. */
        NORMAL,
        /** Die Sonne bleibt den ganzen Tag ueber der Hoehe (z.B. Polartag). */
        ALWAYS_ABOVE,
        /** Die Sonne bleibt den ganzen Tag unter der Hoehe (z.B. Polarnacht). */
        ALWAYS_BELOW
    }

    /**
     * Morgen- und Abendzeitpunkt fuer eine Ereignisart.
     * Bei SUNRISE_SUNSET ist dawn der Aufgang und dusk der Untergang,
     * bei den Daemmerungen der Beginn der Morgendaemmerung bzw. das
     * Ende der Abenddaemmerung.
     */
    public static final class Period {
        private final State state;
        private final ZonedDateTime dawn;
        private final ZonedDateTime dusk;

        Period(State state, ZonedDateTime dawn, ZonedDateTime dusk) {
            this.state = state;
            this.dawn = dawn;
            this.dusk = dusk;
        }

        public State state() {
            return state;
        }

        /** Morgens: Sonne erreicht die Hoehe von unten. Leer, wenn kein Ereignis. */
        public Optional<ZonedDateTime> dawn() {
            return Optional.ofNullable(dawn);
        }

        /** Abends: Sonne unterschreitet die Hoehe. Leer, wenn kein Ereignis. */
        public Optional<ZonedDateTime> dusk() {
            return Optional.ofNullable(dusk);
        }

        @Override
        public String toString() {
            if (state != State.NORMAL) {
                return state.name();
            }
            return dawn + " .. " + dusk;
        }
    }

    /** Ergebnis fuer ein Datum und einen Ort. */
    public static final class Result {
        private final LocalDate date;
        private final double latitude;
        private final double longitude;
        private final ZoneId zone;
        private final ZonedDateTime solarNoon;
        private final Map<Kind, Period> periods;

        Result(LocalDate date, double latitude, double longitude, ZoneId zone,
               ZonedDateTime solarNoon, Map<Kind, Period> periods) {
            this.date = date;
            this.latitude = latitude;
            this.longitude = longitude;
            this.zone = zone;
            this.solarNoon = solarNoon;
            this.periods = periods;
        }

        public LocalDate date() {
            return date;
        }

        public double latitude() {
            return latitude;
        }

        public double longitude() {
            return longitude;
        }

        public ZoneId zone() {
            return zone;
        }

        /** Wahrer Mittag (hoechster Sonnenstand). */
        public ZonedDateTime solarNoon() {
            return solarNoon;
        }

        public Period get(Kind kind) {
            return periods.get(kind);
        }

        public Optional<ZonedDateTime> sunrise() {
            return get(Kind.SUNRISE_SUNSET).dawn();
        }

        public Optional<ZonedDateTime> sunset() {
            return get(Kind.SUNRISE_SUNSET).dusk();
        }

        public Optional<ZonedDateTime> civilDawn() {
            return get(Kind.CIVIL).dawn();
        }

        public Optional<ZonedDateTime> civilDusk() {
            return get(Kind.CIVIL).dusk();
        }

        public Optional<ZonedDateTime> nauticalDawn() {
            return get(Kind.NAUTICAL).dawn();
        }

        public Optional<ZonedDateTime> nauticalDusk() {
            return get(Kind.NAUTICAL).dusk();
        }

        public Optional<ZonedDateTime> astronomicalDawn() {
            return get(Kind.ASTRONOMICAL).dawn();
        }

        public Optional<ZonedDateTime> astronomicalDusk() {
            return get(Kind.ASTRONOMICAL).dusk();
        }

        /** Mehrzeilige, lesbare Darstellung. */
        @Override
        public String toString() {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("HH:mm:ss");
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Datum %s, Breite %.4f, Laenge %.4f, Zone %s%n",
                    date, latitude, longitude, zone));
            sb.append(String.format("%-28s %s%n", "Wahrer Mittag", solarNoon.format(f)));
            Kind[] order = { Kind.ASTRONOMICAL, Kind.NAUTICAL, Kind.CIVIL, Kind.SUNRISE_SUNSET };
            for (Kind k : order) {
                Period p = periods.get(k);
                String dawn = p.dawn().map(z -> z.format(f)).orElse("--:--:--");
                String dusk = p.dusk().map(z -> z.format(f)).orElse("--:--:--");
                String note = p.state() == State.NORMAL ? "" : "  (" + p.state() + ")";
                sb.append(String.format("%-28s %s  %s%s%n", k.label(), dawn, dusk, note));
            }
            return sb.toString();
        }
    }

    private static final double J2000 = 2451545.0;
    private static final double MINUTES_PER_DAY = 1440.0;
    private static final int ITERATIONS = 3;

    private Twilight() {
    }

    /**
     * Berechnet alle Ereignisse fuer den angegebenen Kalendertag im
     * angegebenen Zeitzonenkontext.
     *
     * @param date      Kalendertag (in der Zeitzone {@code zone})
     * @param latitude  geographische Breite in Grad, Nord positiv (-90..90)
     * @param longitude geographische Laenge in Grad, Ost positiv (-180..180)
     * @param zone      Zeitzone fuer Datum und Ergebnis
     */
    public static Result compute(LocalDate date, double latitude, double longitude, ZoneId zone) {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Breite ausserhalb -90..90: " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Laenge ausserhalb -180..180: " + longitude);
        }
        if (date == null || zone == null) {
            throw new IllegalArgumentException("Datum und Zeitzone muessen gesetzt sein");
        }

        // Ortsmittag des Kalendertages als Anker: alle Ereignisse liegen
        // innerhalb von +/- 12 h um den wahren Mittag dieses Tages.
        Instant localNoon = date.atTime(LocalTime.NOON).atZone(zone).toInstant();
        Instant solarNoon = solarNoon(localNoon, longitude);

        Map<Kind, Period> periods = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            periods.put(kind, period(solarNoon, latitude, longitude, kind, zone));
        }
        return new Result(date, latitude, longitude, zone,
                solarNoon.atZone(zone), periods);
    }

    /** Wahrer Mittag, iterativ verfeinert, naechstgelegen zum Anker. */
    private static Instant solarNoon(Instant anchor, double longitude) {
        Instant noon = anchor;
        for (int i = 0; i < ITERATIONS; i++) {
            SunPosition sp = SunPosition.at(noon);
            // Minuten nach 0h UTC, an denen die wahre Sonnenzeit 12:00 ist.
            double minutesUtc = 720.0 - 4.0 * longitude - sp.eqTimeMinutes;
            noon = nearestOccurrence(minutesUtc, anchor);
        }
        return noon;
    }

    private static Period period(Instant solarNoon, double latitude, double longitude,
                                 Kind kind, ZoneId zone) {
        // Erst grob am wahren Mittag pruefen, ob das Ereignis ueberhaupt eintritt.
        SunPosition spNoon = SunPosition.at(solarNoon);
        double cosHa = cosHourAngle(latitude, spNoon.declinationDeg, kind.altitudeDeg());
        if (cosHa > 1.0) {
            return new Period(State.ALWAYS_BELOW, null, null);
        }
        if (cosHa < -1.0) {
            return new Period(State.ALWAYS_ABOVE, null, null);
        }

        Instant dawn = refine(solarNoon, latitude, longitude, kind, -1);
        Instant dusk = refine(solarNoon, latitude, longitude, kind, +1);
        if (dawn == null || dusk == null) {
            // Grenzfall: am Mittag knapp erreichbar, zum Ereigniszeitpunkt nicht mehr.
            State s = cosHa > 0 ? State.ALWAYS_BELOW : State.ALWAYS_ABOVE;
            return new Period(s, null, null);
        }
        return new Period(State.NORMAL, dawn.atZone(zone), dusk.atZone(zone));
    }

    /**
     * Verfeinert den Ereigniszeitpunkt, indem Deklination und Zeitgleichung
     * am jeweils zuletzt berechneten Zeitpunkt neu ausgewertet werden.
     *
     * @param sign -1 fuer morgens, +1 fuer abends
     */
    private static Instant refine(Instant solarNoon, double latitude, double longitude,
                                  Kind kind, int sign) {
        Instant t = solarNoon;
        for (int i = 0; i < ITERATIONS; i++) {
            SunPosition sp = SunPosition.at(t);
            double cosHa = cosHourAngle(latitude, sp.declinationDeg, kind.altitudeDeg());
            if (cosHa > 1.0 || cosHa < -1.0) {
                return null;
            }
            double haDeg = Math.toDegrees(Math.acos(cosHa));
            double noonMinutesUtc = 720.0 - 4.0 * longitude - sp.eqTimeMinutes;
            double eventMinutesUtc = noonMinutesUtc + sign * 4.0 * haDeg;
            t = nearestOccurrence(eventMinutesUtc, solarNoon.plusSeconds(sign * 6L * 3600L));
        }
        return t;
    }

    /**
     * cos(Stundenwinkel) fuer die gegebene Sonnenhoehe.
     * Werte ausserhalb [-1, 1] bedeuten, dass die Hoehe nicht erreicht wird.
     */
    private static double cosHourAngle(double latitudeDeg, double declinationDeg, double altitudeDeg) {
        double lat = Math.toRadians(latitudeDeg);
        double decl = Math.toRadians(declinationDeg);
        double alt = Math.toRadians(altitudeDeg);
        return (Math.sin(alt) - Math.sin(lat) * Math.sin(decl))
                / (Math.cos(lat) * Math.cos(decl));
    }

    /**
     * Wandelt "Minuten nach 0h UTC" in den Zeitpunkt um, der dem Anker am
     * naechsten liegt. Damit bleibt das Ergebnis auch bei Zeitzonen weit
     * entfernt von UTC auf dem richtigen Kalendertag.
     */
    private static Instant nearestOccurrence(double minutesUtc, Instant anchor) {
        long anchorMillis = anchor.toEpochMilli();
        long dayStartMillis = Math.floorDiv(anchorMillis, 86_400_000L) * 86_400_000L;
        long candidate = dayStartMillis + Math.round(minutesUtc * 60_000.0);
        long halfDay = 43_200_000L;
        while (candidate - anchorMillis > halfDay) {
            candidate -= 86_400_000L;
        }
        while (anchorMillis - candidate > halfDay) {
            candidate += 86_400_000L;
        }
        return Instant.ofEpochMilli(candidate);
    }

    /** Deklination und Zeitgleichung der Sonne zu einem Zeitpunkt (NOAA). */
    private static final class SunPosition {
        final double declinationDeg;
        final double eqTimeMinutes;

        private SunPosition(double declinationDeg, double eqTimeMinutes) {
            this.declinationDeg = declinationDeg;
            this.eqTimeMinutes = eqTimeMinutes;
        }

        static SunPosition at(Instant instant) {
            double jd = 2440587.5 + instant.toEpochMilli() / 86_400_000.0;
            double t = (jd - J2000) / 36525.0; // Julianische Jahrhunderte seit J2000

            double l0 = mod360(280.46646 + t * (36000.76983 + t * 0.0003032));
            double m = 357.52911 + t * (35999.05029 - 0.0001537 * t);
            double e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t);

            double mRad = Math.toRadians(m);
            double c = Math.sin(mRad) * (1.914602 - t * (0.004817 + 0.000014 * t))
                    + Math.sin(2 * mRad) * (0.019993 - 0.000101 * t)
                    + Math.sin(3 * mRad) * 0.000289;
            double trueLong = l0 + c;
            double omega = 125.04 - 1934.136 * t;
            double appLong = trueLong - 0.00569 - 0.00478 * Math.sin(Math.toRadians(omega));

            double eps0 = 23.0 + (26.0 + (21.448
                    - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0;
            double eps = eps0 + 0.00256 * Math.cos(Math.toRadians(omega));
            double epsRad = Math.toRadians(eps);

            double decl = Math.toDegrees(Math.asin(
                    Math.sin(epsRad) * Math.sin(Math.toRadians(appLong))));

            double y = Math.tan(epsRad / 2.0);
            y *= y;
            double l0Rad = Math.toRadians(l0);
            double eqTime = y * Math.sin(2 * l0Rad)
                    - 2.0 * e * Math.sin(mRad)
                    + 4.0 * e * y * Math.sin(mRad) * Math.cos(2 * l0Rad)
                    - 0.5 * y * y * Math.sin(4 * l0Rad)
                    - 1.25 * e * e * Math.sin(2 * mRad);
            eqTime = 4.0 * Math.toDegrees(eqTime);

            return new SunPosition(decl, eqTime);
        }

        private static double mod360(double x) {
            double r = x % 360.0;
            return r < 0 ? r + 360.0 : r;
        }
    }

    /**
     * Aufruf von der Kommandozeile:
     * <pre>java pub.Astro.Twilight BREITE LAENGE [DATUM] [ZEITZONE]</pre>
     * Datum als JJJJ-MM-TT (Standard: heute), Zeitzone als IANA-Name
     * (Standard: Systemzone), z.B. Europe/Berlin.
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Aufruf: java pub.Astro.Twilight BREITE LAENGE [JJJJ-MM-TT] [ZEITZONE]");
            System.exit(1);
        }
        double lat = Double.parseDouble(args[0]);
        double lon = Double.parseDouble(args[1]);
        ZoneId zone = args.length > 3 ? ZoneId.of(args[3]) : ZoneId.systemDefault();
        LocalDate date = args.length > 2 ? LocalDate.parse(args[2]) : LocalDate.now(zone);
        System.out.print(compute(date, lat, lon, zone));
    }
}
