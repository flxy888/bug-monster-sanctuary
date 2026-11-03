import java.util.ArrayList;
import java.util.List;

public final class BackorderReminderPlanner {
    public record Customer(String email, boolean remindersEnabled, List<Order> orders) {}
    public record Order(String orderId, List<LineItem> items) {}
    public record LineItem(String sku, int quantity, boolean backOrdered) {}
    public record Reminder(String email, String orderId, String sku, int quantity) {}

    public List<Reminder> plan(List<Customer> customers) {
        var reminders = new ArrayList<Reminder>();
        for (Customer customer : customers) {
            if (!customer.remindersEnabled()) {
                continue;
            }
            for (Order order : customer.orders()) {
                collectOrderReminders(customer.email(), order, reminders);
            }
        }
        return List.copyOf(reminders);
    }

    private void collectOrderReminders(String email, Order order, List<Reminder> reminders) {
        for (LineItem item : order.items()) {
            if (item.backOrdered() && item.quantity() > 0) {
                reminders.add(new Reminder(email, order.orderId(), item.sku(), item.quantity()));
            }
        }
    }
}
