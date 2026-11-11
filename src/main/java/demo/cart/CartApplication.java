package demo.cart;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

// CLI cart application.
@SpringBootApplication
public class CartApplication implements CommandLineRunner {
    private final CartService cart;
    private final GeminiAssistant assistant;

    public CartApplication(JdbcTemplate db) {
        cart = new CartService(db);
        assistant = new GeminiAssistant(cart);
    }

    public static void main(String[] args) {
        try (var context = SpringApplication.run(CartApplication.class, args)) {
            // Close Spring after the command loop exits.
        }
    }

    @Override
    public void run(String... args) {
        System.out.println("\nAI Shopping Cart | Spring Boot + MySQL + Gemini");
        help();
        if (!assistant.enabled())
            System.out.println("Gemini disabled: paste your key into GEMINI_API_KEY.txt to enable 'ai'.");

        try (var input = new Scanner(System.in)) {
            while (true) {
                System.out.print("cart> ");
                System.out.flush(); // Show the prompt before waiting for input.
                if (!input.hasNextLine()) return;
                String line = input.nextLine().trim();
                if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) return;
                if (line.isEmpty()) continue;
                try {
                    command(line);
                } catch (IllegalArgumentException e) {
                    System.out.println("Error: " + e.getMessage());
                } catch (RuntimeException e) {
                    System.err.println("Error: " + e);
                    e.printStackTrace(System.err);
                }
            }
        }
    }

    // Translate a terminal command into a cart or AI action.
    private void command(String line) {
        String[] args = line.split("\\s+", 3);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "products" -> products();
            case "cart" -> showCart();
            case "clear" -> { cart.clear(); showCart(); }
            case "add", "set" -> {
                if (args.length != 3) throw new IllegalArgumentException("Usage: " + args[0] + " ID QTY");
                int id = Integer.parseInt(args[1]);
                int qty = Integer.parseInt(args[2]);
                if (args[0].equalsIgnoreCase("add")) cart.add(id, qty);
                else cart.set(id, qty);
                showCart();
            }
            case "ai" -> {
                if (line.length() <= 3 || line.substring(3).isBlank())
                    throw new IllegalArgumentException("Usage: ai MESSAGE");
                System.out.println("Gemini: " + assistant.ask(line.substring(3).trim()));
                showCart();
            }
            case "help" -> help();
            default -> System.out.println("Unknown command; type help.");
        }
    }

    private static void help() {
        System.out.println("Commands: products | cart | add ID QTY | set ID QTY | clear | ai MESSAGE | help | quit");
    }

    private static String money(Object amount) {
        return String.format(Locale.US, "$%.2f", (BigDecimal) amount);
    }

    private void products() {
        for (var item : cart.products())
            System.out.printf("  %s  %-10s %s%n", item.get("id"), item.get("name"), money(item.get("price")));
    }

    @SuppressWarnings("unchecked")
    private void showCart() {
        var result = cart.view();
        var items = (List<Map<String, Object>>) result.get("items");
        if (items.isEmpty()) System.out.println("  (empty)");
        for (var item : items)
            System.out.printf("  %s  %s x %s = %s%n", item.get("product_id"), item.get("name"),
                              item.get("quantity"), money(item.get("subtotal")));
        System.out.println("  Total: " + money(result.get("total")));
    }
}
