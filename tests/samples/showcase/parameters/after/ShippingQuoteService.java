import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class ShippingQuoteService {
    public record ShipmentRequest(String destinationZone, BigDecimal parcelWeightKg,
                                  BigDecimal declaredValue, boolean express,
                                  boolean fragile, boolean insured) {}

    public BigDecimal quoteShipping(ShipmentRequest request) {
        Objects.requireNonNull(request.destinationZone(), "destinationZone");
        if (request.parcelWeightKg().signum() <= 0 || request.declaredValue().signum() < 0) {
            throw new IllegalArgumentException("Invalid weight or declared value");
        }
        BigDecimal baseRate = switch (request.destinationZone()) {
            case "LOCAL" -> new BigDecimal("6.00");
            case "REGIONAL" -> new BigDecimal("10.00");
            case "REMOTE" -> new BigDecimal("18.00");
            default -> throw new IllegalArgumentException("Unknown destination zone");
        };
        BigDecimal fee = baseRate.add(request.parcelWeightKg().multiply(new BigDecimal("1.20")));
        if (request.express()) {
            fee = fee.add(new BigDecimal("8.00"));
        }
        if (request.fragile()) {
            fee = fee.add(new BigDecimal("3.00"));
        }
        if (request.insured()) {
            fee = fee.add(request.declaredValue().multiply(new BigDecimal("0.005")));
        }
        return fee.setScale(2, RoundingMode.HALF_UP);
    }
}
