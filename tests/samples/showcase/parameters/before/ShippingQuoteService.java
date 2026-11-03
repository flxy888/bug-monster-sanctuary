import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class ShippingQuoteService {
    public BigDecimal quoteShipping(String destinationZone, BigDecimal parcelWeightKg,
                                    BigDecimal declaredValue, boolean express,
                                    boolean fragile, boolean insured) {
        Objects.requireNonNull(destinationZone, "destinationZone");
        if (parcelWeightKg.signum() <= 0 || declaredValue.signum() < 0) {
            throw new IllegalArgumentException("Invalid weight or declared value");
        }
        BigDecimal baseRate = switch (destinationZone) {
            case "LOCAL" -> new BigDecimal("6.00");
            case "REGIONAL" -> new BigDecimal("10.00");
            case "REMOTE" -> new BigDecimal("18.00");
            default -> throw new IllegalArgumentException("Unknown destination zone");
        };
        BigDecimal fee = baseRate.add(parcelWeightKg.multiply(new BigDecimal("1.20")));
        if (express) {
            fee = fee.add(new BigDecimal("8.00"));
        }
        if (fragile) {
            fee = fee.add(new BigDecimal("3.00"));
        }
        if (insured) {
            fee = fee.add(declaredValue.multiply(new BigDecimal("0.005")));
        }
        return fee.setScale(2, RoundingMode.HALF_UP);
    }
}
