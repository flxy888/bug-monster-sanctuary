import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class SalesReportExporter {
    public record Sale(String orderId, String productCode, int quantity, BigDecimal unitPrice) {}

    public String export(LocalDate reportDate, List<Sale> sales) {
        Objects.requireNonNull(reportDate, "reportDate");
        Objects.requireNonNull(sales, "sales");
        List<Sale> entries = List.copyOf(sales);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("No sales to export");
        }
        BigDecimal revenue = BigDecimal.ZERO;
        int units = 0;
        for (Sale sale : entries) {
            if (sale.quantity() <= 0 || sale.unitPrice().signum() < 0) {
                throw new IllegalArgumentException("Invalid quantity or price");
            }
            validateCell(sale.orderId());
            validateCell(sale.productCode());
            units += sale.quantity();
            revenue = revenue.add(sale.unitPrice().multiply(BigDecimal.valueOf(sale.quantity())));
        }
        BigDecimal average = revenue.divide(BigDecimal.valueOf(entries.size()), 2, RoundingMode.HALF_UP);
        StringBuilder csv = new StringBuilder();
        csv.append("report_date,").append(reportDate).append('\n');
        csv.append("order_id,product_code,quantity,unit_price,line_total\n");
        for (Sale sale : entries) {
            BigDecimal lineTotal = sale.unitPrice().multiply(BigDecimal.valueOf(sale.quantity()));
            csv.append(sale.orderId()).append(',');
            csv.append(sale.productCode()).append(',');
            csv.append(sale.quantity()).append(',');
            csv.append(sale.unitPrice().setScale(2, RoundingMode.HALF_UP)).append(',');
            csv.append(lineTotal.setScale(2, RoundingMode.HALF_UP)).append('\n');
        }
        csv.append("total_lines,").append(entries.size()).append('\n');
        csv.append("total_units,").append(units).append('\n');
        csv.append("total_revenue,").append(revenue.setScale(2, RoundingMode.HALF_UP)).append('\n');
        csv.append("average_line_value,").append(average).append('\n');
        return csv.toString();
    }

    private void validateCell(String value) {
        if (value.isBlank() || value.contains(",") || value.contains("\n") || value.contains("\r")) {
            throw new IllegalArgumentException("Expected a nonblank single CSV field");
        }
    }
}
