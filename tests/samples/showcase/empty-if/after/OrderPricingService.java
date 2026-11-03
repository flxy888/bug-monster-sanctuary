import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class OrderPricingService {
    public record Order(String orderId, boolean vip, BigDecimal subtotal) {}

    public BigDecimal totalDue(Order order) {
        Objects.requireNonNull(order, "order");
        if (order.subtotal().signum() < 0) {
            throw new IllegalArgumentException("Subtotal must not be negative");
        }
        BigDecimal discount = BigDecimal.ZERO;
        if (order.vip())
        {
            discount = order.subtotal().multiply(new BigDecimal("0.10"))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return order.subtotal().subtract(discount).setScale(2, RoundingMode.HALF_UP);
    }
}
