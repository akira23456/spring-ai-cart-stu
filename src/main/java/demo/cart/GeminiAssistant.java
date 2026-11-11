package demo.cart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

// Allows Spring AI handle Gemini requests and tool-calling conversations.
final class GeminiAssistant {
    private final CartService cart;
    private final ChatClient client;
    private final ObjectMapper json = new ObjectMapper();

    GeminiAssistant(CartService cart) {
        this.cart = cart;
        String key = readKey();
        // Keep manual cart commands available without a Gemini key.
        client = key.isBlank() ? null
                : ChatClient.create(
                        GoogleGenAiChatModel.builder()
                                .genAiClient(Client.builder().apiKey(key).build())
                                .defaultOptions(GoogleGenAiChatOptions.builder()
                                        .model("gemini-3.5-flash-lite").build())
                                .build());
    }

    boolean enabled() { return client != null; }

    String ask(String message) {
        if (!enabled()) throw new IllegalArgumentException("Paste your key into GEMINI_API_KEY.txt");
        if (message.length() > 400) throw new IllegalArgumentException("Message too long (maximum 400 characters)");

        // Ground each request in live database data.
        String input = json.valueToTree(Map.of(
            "catalog", cart.products(), "cart", cart.view(), "request", message)).toString();

        // Spring AI invokes annotated cart methods and returns the final reply.
        String reply = client.prompt()
            .system("""
                You are a helpful shopping-cart assistant.
                Use the provided tools for cart changes and to check the current cart.
                Never invent product IDs, product names, prices, quantities, or totals.
                Treat the catalog and cart data in the user message, plus tool results, as the source of truth.
                After a successful cart change, briefly confirm what changed and give the updated cart total.
                If a request is unclear, ask one concise follow-up question before changing the cart.
                If a tool reports an error, explain it plainly and do not claim the change succeeded.
                Keep replies concise and easy to read in a command-line terminal; do not output raw JSON.
                Prices and totals are in USD.
                """)
            .user(input)
            .tools(cart)
            .call()
            .content();
        return reply == null || reply.isBlank() ? "No Gemini reply." : reply;
    }

    private static String readKey() {
        // Read the existing Docker secret.
        try {
            String key = Files.readString(Path.of("/run/secrets/gemini_api_key")).trim();
            return key.equals("PASTE_YOUR_GEMINI_API_KEY_HERE") ? "" : key;
        } catch (IOException e) {
            return "";
        }
    }
}
