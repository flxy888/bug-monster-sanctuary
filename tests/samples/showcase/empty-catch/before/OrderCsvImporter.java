import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class OrderCsvImporter {
    public record OrderLine(String orderId, String sku, BigDecimal amount) {}
    public record ImportFailure(int lineNumber, String reason) {}
    public record ImportResult(List<OrderLine> orders, List<ImportFailure> failures) {}

    public ImportResult importLines(List<String> rows) {
        var orders = new ArrayList<OrderLine>();
        var failures = new ArrayList<ImportFailure>();
        for (int index = 0; index < rows.size(); index++) {
            String row = rows.get(index);
            if (row.isBlank()) {
                continue;
            }
            String[] fields = row.split(",", -1);
            if (fields.length != 3) {
                failures.add(new ImportFailure(index + 1, "Expected orderId,sku,amount"));
                continue;
            }
            try {
                BigDecimal amount = new BigDecimal(fields[2].strip());
                orders.add(new OrderLine(fields[0].strip(), fields[1].strip(), amount));
            } catch (NumberFormatException exception) {
            }
        }
        return new ImportResult(List.copyOf(orders), List.copyOf(failures));
    }
}
