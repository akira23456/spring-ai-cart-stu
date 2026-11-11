package demo.cart;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// SQL and validation. CLI and AI use the these cart operations.
final class CartService {
    private final JdbcTemplate db;

    CartService(JdbcTemplate db) {
        this.db = db;
    }

    List<Map<String, Object>> products() {
        return db.queryForList("SELECT id, name, price FROM products ORDER BY id");
    }

    @Tool(name = "get_cart", description = "Read the cart and total in USD")
    Map<String, Object> view() {
        var items = db.queryForList("""
            SELECT p.id AS product_id, p.name, p.price, c.quantity,
                   p.price * c.quantity AS subtotal
            FROM cart c JOIN products p ON p.id = c.product_id ORDER BY p.id
            """);
        BigDecimal total = items.stream().map(item -> (BigDecimal) item.get("subtotal"))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Map.of("items", items, "total", total);
    }

    // This adds a new item or increments quantity.
    @Tool(name = "add_to_cart", description = "Add units to a product in the cart")
    Map<String, Object> add(@ToolParam(description = "Catalog product ID") int id,
                            @ToolParam(description = "Units to add (1-99)") int qty) {
        // validate product id and quantity.
        validate(id, qty);
        if (qty == 0) throw new IllegalArgumentException("Quantity to add must be at least 1");

        // check current quantity to max it at 99 units
        var currentQuantities = db.queryForList(
            "SELECT quantity FROM cart WHERE product_id = ?", Integer.class, id);
    
        // if product is not in the cart, currentQuantities will be empty, so we treat it as 0.
        int currentQty = currentQuantities.isEmpty() ? 0 : currentQuantities.get(0);
        
        // check if adding the new quantity would exceed the limit of 99 units
        if (currentQty + qty > 99)
            throw new IllegalArgumentException("Adding " + qty + " units would exceed the 99-unit limit");

        // update the cart with the new quantity using sql
        db.update("""
            INSERT INTO cart(product_id, quantity) VALUES (?, ?)
            ON DUPLICATE KEY UPDATE quantity = quantity + ?
            """, id, qty, qty);
        return view();
    }

    // This changes an existing item.
    @Tool(name = "set_quantity", description = "Set exact quantity. Zero removes a product")
    Map<String, Object> set(@ToolParam(description = "Catalog product ID") int id,
                            @ToolParam(description = "Exact units") int qty) {
        validate(id, qty);
        // Zero removes the item. Otherwise replace its quantity.
        if (qty == 0) db.update("DELETE FROM cart WHERE product_id = ?", id);
        else db.update("""
            INSERT INTO cart(product_id, quantity) VALUES (?, ?)
            ON DUPLICATE KEY UPDATE quantity = ?
            """, id, qty, qty);
        return view();
    }

    @Tool(name = "clear_cart", description = "Empty the cart")
    Map<String, Object> clear() {
        db.update("DELETE FROM cart");
        return view();
    }

    // Product and quantity checks.
    private void validate(int id, int qty) {
        if (id < 1 || id > 1_000_000 || qty < 0 || qty > 99)
            throw new IllegalArgumentException("Invalid product ID or quantity (maximum 99)");
        if (db.queryForObject("SELECT COUNT(*) FROM products WHERE id = ?", Integer.class, id) == 0)
            throw new IllegalArgumentException("Unknown product ID: " + id);
    }
}
