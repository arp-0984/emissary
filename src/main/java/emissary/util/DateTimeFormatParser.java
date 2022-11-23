package emissary.util;

import emissary.config.Configurator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class DateTimeFormatParser {

    protected static final List<DateTimeFormatter> dateTimeZoneFormats = new ArrayList<>();
    protected static final List<DateTimeFormatter> dateTimeOffsetFormats = new ArrayList<>();
    protected static final List<DateTimeFormatter> dateTimeFormats = new ArrayList<>();
    protected static final List<DateTimeFormatter> dateFormats = new ArrayList<>();

    private static final ZoneId GMT = ZoneId.of("GMT");

    protected static final Logger logger = LoggerFactory.getLogger(DateTimeFormatParserLegacy.class);

    static {
        configure();
    }

    protected static void configure() {

        Configurator configG;
        try {
            configG = emissary.config.ConfigUtil.getConfigInfo(DateTimeFormatParser.class);
        } catch (IOException e) {
            logger.error("Cannot open default config file", e);
            return;
        }

        for (final String dfentry : configG.findEntries("DATE_TIME_ZONE_FORMAT")) {
            try {
                final DateTimeFormatter sdf =
                        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(dfentry).toFormatter().withZone(GMT);
                dateTimeZoneFormats.add(sdf);
            } catch (Exception ex) {
                logger.debug("DATE_TIME_ZONE_FORMAT entry '{}' cannot be parsed", dfentry, ex);
            }
        }
        logger.debug("Loaded {} DATE_TIME_ZONE_FORMAT entries", dateTimeZoneFormats.size());
        for (final String dfentry : configG.findEntries("DATE_TIME_OFFSET_FORMAT")) {
            try {
                final DateTimeFormatter sdf =
                        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(dfentry).toFormatter();
                dateTimeOffsetFormats.add(sdf);
            } catch (Exception ex) {
                logger.debug("DATE_TIME_OFFSET_FORMAT entry '{}' cannot be parsed", dfentry, ex);
            }
        }
        logger.debug("Loaded {} DATE_TIME_OFFSET_FORMAT entries", dateFormats.size());
        for (final String dfentry : configG.findEntries("DATE_TIME_FORMAT")) {
            try {
                final DateTimeFormatter sdf =
                        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(dfentry).toFormatter();
                dateTimeFormats.add(sdf);
            } catch (Exception ex) {
                logger.debug("DATE_TIME_FORMAT entry '{}' cannot be parsed", dfentry, ex);
            }
        }
        logger.debug("Loaded {} DATE_TIME_FORMAT entries", dateTimeFormats.size());


        for (final String dfentry : configG.findEntries("DATE_FORMAT")) {
            try {
                final DateTimeFormatter sdf =
                        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(dfentry).toFormatter();
                dateFormats.add(sdf);
            } catch (Exception ex) {
                logger.debug("DATE_FORMAT entry '{}' cannot be parsed", dfentry, ex);
            }
        }
    }

    /**
     * Parse an RFC-822 Date or one of the thousands of variants make a quick attempt to normalize the timezone information
     * and get the timestamp in GMT. Should change to pass in a default from the U124 header
     *
     * @param dateString the string date from the RFC 822 Date header
     * @param supplyDefaultOnBad when true use current date if sentDate is unparseable
     * @return the GMT time of the event or NOW if unparseable, or null if supplyDefaultOnBad is false
     */
    public static LocalDateTime parseDate(final String dateString, final boolean supplyDefaultOnBad) {
        LocalDateTime date = null;

        if (dateString != null && dateString.length() > 0) {
            // Take it apart and stick it back together to
            // get rid of multiple contiguous spaces
            String instr = dateString.replaceAll("\t+", " "); // tabs
            instr = instr.replaceAll("[ ]+", " "); // multiple spaces
            instr = instr.replaceAll("=0D$", ""); // common qp'ified ending

            date = tryParseWithDateTimeZoneFormats(instr);
            if (date == null) {
                date = tryParseWithDateTimeOffsetFormats(instr);
            }
            if (date == null) {
                date = tryParseWithDateTimeFormats(instr);
            }
            if (date == null) {
                date = tryParseWithDateFormats(instr);
            }
            if (date == null) {
                try {
                    date = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(instr)).atZone(GMT).toLocalDateTime();
                } catch (DateTimeParseException e) {
                    // ignore
                }
            }
        }

        // Use the default if required
        if (date == null && supplyDefaultOnBad) {
            date = LocalDateTime.now();
        }

        return date;
    }

    /**
     * Attempt to parse the string instr with one of the ZonedDateTime patterns
     *
     * @param instr the string to attempt to format
     * @return the LocalDateTime object if a formatter worked, or null otherwise
     */
    private static LocalDateTime tryParseWithDateTimeZoneFormats(final String instr) {
        // formats with a time zone
        for (final DateTimeFormatter dtf : dateTimeZoneFormats) {
            try {
                ZonedDateTime zdt = ZonedDateTime.parse(instr, dtf);
                return ZonedDateTime.ofInstant(zdt.toInstant(), GMT).toLocalDateTime();
            } catch (DateTimeParseException e) {
                // ignore
                logger.debug("Error: " + e);
            }
        }
        return null;
    }

    /**
     * Attempt to parse the string instr with one of the LocalDateTime patterns
     *
     * @param instr the string to attempt to format
     * @return the LocalDateTime object if a formatter worked, or null otherwise
     */
    private static LocalDateTime tryParseWithDateTimeFormats(final String instr) {
        // formats with a date and time and no zone/offset
        for (final DateTimeFormatter dtf : dateTimeFormats) {
            try {
                LocalDateTime l = LocalDateTime.parse(instr, dtf);
                return l;
            } catch (DateTimeParseException e) {
                // ignore
                logger.debug("Error: " + e);
            }
        }
        return null;
    }

    /**
     * Attempt to parse the string instr with one of the OffsetDateTime patterns
     *
     * @param instr the string to attempt to format
     * @return the LocalDateTime object if a formatter worked, or null otherwise
     */
    private static LocalDateTime tryParseWithDateTimeOffsetFormats(final String instr) {
        // formats with a time zone offset
        for (final DateTimeFormatter dtf : dateTimeOffsetFormats) {
            try {
                OffsetDateTime odt = OffsetDateTime.parse(instr, dtf);
                return OffsetDateTime.ofInstant(odt.toInstant(), GMT).toLocalDateTime();
            } catch (DateTimeParseException e) {
                // ignore
                logger.debug("Error: " + e);
            }
        }
        return null;
    }

    /**
     * Attempt to parse the string instr with one of the LocalDate patterns
     *
     * @param instr the string to attempt to format
     * @return the LocalDateTime object if a formatter worked, or null otherwise
     */
    private static LocalDateTime tryParseWithDateFormats(final String instr) {
        // formats with a date but no time
        for (final DateTimeFormatter dtf : dateFormats) {
            try {
                LocalDate d = LocalDate.parse(instr, dtf);
                return d.atStartOfDay();
            } catch (DateTimeParseException e) {
                // ignore
                logger.debug("Error: " + e);
            }
        }
        return null;
    }
}
