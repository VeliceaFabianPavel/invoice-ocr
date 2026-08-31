package com.invoiceocr.extraction.validation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * The identity every invoice obeys: {@code net + VAT = total}.
 *
 * <p>This is the strongest piece of evidence the application has, and until now
 * it went unused. Three numbers read independently off a page are three chances
 * to be wrong; three numbers that have to add up are a system of equations, and
 * knowing any two of them gives the third exactly. It turns the totals block
 * from three guesses into one answer.</p>
 *
 * <p>The VAT rate is the second constraint. A ratio of VAT to net that lands on
 * a rate actually in force is corroboration; one that lands nowhere near says
 * that at least one of the two figures was misread, which is worth knowing even
 * when it cannot be fixed.</p>
 */
public final class InvoiceArithmetic {

    /** Rounding slack: an invoice may round each line before summing them. */
    public static final BigDecimal TOLERANCE = new BigDecimal("0.02");

    /** How far a ratio may sit from a real rate and still be taken as that rate. */
    private static final BigDecimal RATE_TOLERANCE = new BigDecimal("0.6");

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * VAT rates a Romanian invoice can carry, current and recent.
     *
     * <p>The standard rate moved from 19% to 21% and the reduced 9% to 11% in
     * August 2025, so both generations are listed: invoices from before the
     * change keep circulating for years, and an archive scanned today is mostly
     * old ones.</p>
     */
    public static final List<BigDecimal> VAT_RATES = List.of(
            BigDecimal.valueOf(21), BigDecimal.valueOf(19), BigDecimal.valueOf(11),
            BigDecimal.valueOf(9), BigDecimal.valueOf(5), BigDecimal.ZERO);

    /** True when the three figures add up, within the rounding tolerance. */
    public static boolean addsUp(BigDecimal net, BigDecimal vat, BigDecimal total) {
        if (net == null || vat == null || total == null) {
            return false;
        }
        return net.add(vat).subtract(total).abs().compareTo(TOLERANCE) <= 0;
    }

    /** The VAT rate implied by a net and a VAT figure, when it is one that exists. */
    public static Optional<BigDecimal> impliedRate(BigDecimal net, BigDecimal vat) {
        if (net == null || vat == null || net.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal percent = vat.multiply(HUNDRED).divide(net, 4, RoundingMode.HALF_UP);
        return VAT_RATES.stream()
                .filter(rate -> percent.subtract(rate).abs().compareTo(RATE_TOLERANCE) <= 0)
                .findFirst();
    }

    /** True when the pair implies a VAT rate that is actually charged. */
    public static boolean plausiblePair(BigDecimal net, BigDecimal vat) {
        return impliedRate(net, vat).isPresent();
    }

    /** The VAT due on {@code net} at {@code ratePercent}. */
    public static BigDecimal vatOn(BigDecimal net, BigDecimal ratePercent) {
        return scale(net.multiply(ratePercent).divide(HUNDRED, 6, RoundingMode.HALF_UP));
    }

    /** The net that a gross {@code total} contains at {@code ratePercent}. */
    public static BigDecimal netOf(BigDecimal total, BigDecimal ratePercent) {
        return scale(total.multiply(HUNDRED)
                .divide(HUNDRED.add(ratePercent), 6, RoundingMode.HALF_UP));
    }

    /** Two decimals, half-up: how money is printed. */
    public static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private InvoiceArithmetic() {
        throw new AssertionError("No instances");
    }
}
