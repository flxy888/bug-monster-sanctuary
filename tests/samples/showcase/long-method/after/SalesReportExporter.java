import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class SalesReportExporter {
    public record Sale(String orderId, String productCode, int quantity, BigDecimal unitPrice) {}
    private record Totals(int units, BigDecimal revenue, BigDecimal average) {}

    public String export(LocalDate reportDate, List<Sale> sales) {
        Objects.requireNonNull(reportDate, "reportDate");
        Objects.requireNonNull(sales, "sales");
        List<Sale> entries = List.copyOf(sales);
        validateSales(entries);
        Totals totals = summarize(entries);
        StringBuilder csv = new StringBuilder();
        csv.append("report_date,").append(reportDate).append('\n');
        csv.append("order_id,product_code,quantity,unit_price,line_total\n");
        appendLines(csv, entries);
        appendSummary(csv, entries.size(), totals);
        return csv.toString();
    }

    private void validateSales(List<Sale> entries) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("No sales to export");
        }
        for (Sale sale : entries) {
            if (sale.quantity() <= 0 || sale.unitPrice().signum() < 0) {
                throw new IllegalArgumentException("Invalid quantity or price");
            }
            validateCell(sale.orderId());
            validateCell(sale.productCode());
        }
    }

    private Totals summarize(List<Sale> entries) {
        BigDecimal revenue = BigDecimal.ZERO;
        int units = 0;
        for (Sale sale : entries) {
            units += sale.quantity();
            revenue = revenue.add(sale.unitPrice().multiply(BigDecimal.valueOf(sale.quantity())));
        }
        BigDecimal average = revenue.divide(BigDecimal.valueOf(entries.size()), 2, RoundingMode.HALF_UP);
        return new Totals(units, revenue, average);
    }

    private void appendLines(StringBuilder csv, List<Sale> entries) {
        for (Sale sale : entries) {
            BigDecimal lineTotal = sale.unitPrice().multiply(BigDecimal.valueOf(sale.quantity()));
            csv.append(sale.orderId()).append(',');
            csv.append(sale.productCode()).append(',');
            csv.append(sale.quantity()).append(',');
            csv.append(sale.unitPrice().setScale(2, RoundingMode.HALF_UP)).append(',');
            csv.append(lineTotal.setScale(2, RoundingMode.HALF_UP)).append('\n');
        }
    }

    private void appendSummary(StringBuilder csv, int lineCount, Totals totals) {
        csv.append("total_lines,").append(lineCount).append('\n');
        csv.append("total_units,").append(totals.units()).append('\n');
        csv.append("total_revenue,").append(totals.revenue().setScale(2, RoundingMode.HALF_UP)).append('\n');
        csv.append("average_line_value,").append(totals.average()).append('\n');
    }

    private void validateCell(String value) {
        if (value.isBlank() || value.contains(",") || value.contains("\n") || value.contains("\r")) {
            throw new IllegalArgumentException("Expected a nonblank single CSV field");
        }
    }
}
