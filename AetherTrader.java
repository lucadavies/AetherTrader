import org.apache.commons.codec.binary.Hex;
import org.json.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.UUID;

public class AetherTrader
{
    private String apiKey = "";
    private String apiKeySecret = "";

    public AetherTrader()
    {
        try
        {
            apiKey = Files.readString(Paths.get("key"));
            apiKeySecret = Files.readString(Paths.get("keySecret"));
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error reading API keys. Please check your key files and try again.", e);
        }
    }

    public void showMenu()
    {
        System.out.println("----- MENU -----");
        System.out.println("1. Get BTC Info");
        System.out.println("2. Get balance");
        System.out.println("3. Get open orders");
        System.out.println("4. Cancel order");
        System.out.println("5. Place sell limit order");
        System.out.println("0. Quit");
    }

    public int getChoice(Scanner sc)
    {
        int choice = 0;
        try
        {
            choice = sc.nextInt();
        }
        catch (NoSuchElementException e)
        {
            choice = -1;
        }
        return choice;
    }

    public String getBTCData()
    {
        JSONObject data = new JSONObject(sendPublicRequest("/api/v2/ticker/btceur"));
        return formatJSON(data);
    }

    public String getBalance()
    {
        JSONObject data = new JSONObject(sendPrivateRequest("/api/v2/balance/"));
        JSONObject btcData = new JSONObject(sendPublicRequest("/api/v2/ticker/btceur"));
        float value = Float.parseFloat(data.getString("eur_balance")) + (Float.parseFloat(data.getString("btc_balance")) * Float.parseFloat(btcData.getString("last")));
        List<String> balKeys = Arrays.asList("eur_available", "eur_balance", "btc_balance","btc_available");
        String result = "";
        for (String k : balKeys)
        {
            result += k + ": " + data.get(k) + "\n";
        }
        result += "VALUE: " + value;
        return result;
    }

    public String getOpenOrders()
    {
        JSONArray data = new JSONArray(sendPrivateRequest("/api/v2/open_orders/all/"));
        
        if (data.length() != 0)
        {
            String result = "";
            JSONObject jOrder = null;
            for (Object order : data)
            {
                jOrder =  (JSONObject)order;
                result += jOrder.toString() + "\n";
            }
            return result;
        }
        else
        {
            return "No orders to show.\n";
        }
    }

    public String cancelOrder(Scanner sc)
    {
        sc.nextLine();
        System.out.print("Order ID: ");
        // TODO input checking
        String id = sc.nextLine();
        String[] params = new String[]
        {
            "id=" + id
        };
        JSONObject data = new JSONObject(sendPrivateRequest("/api/v2/cancel_order/", params));
        if (!data.has("error"))
        {
            return "Success. Order " +  data.get("id").toString() + " canceled";
        }
        else
        {
            return "Error. Failed to cancel order.";
        }
    }

    public String placeSellLimitOrder(Scanner sc)
    {
        sc.nextLine();
        System.out.print("Amount (BTC): ");
        BigDecimal amt =  new BigDecimal(sc.next());
        System.out.print("Price (EUR): ");
        BigDecimal price = new BigDecimal(sc.next());
        String[] params = new String[]
        {
            "amount=" + amt,
            "price=" + price
        };
        JSONObject data = new JSONObject(sendPrivateRequest("/api/v2/sell/btceur/", params));
        if (!data.has("error"))
        {
            return "Success. Order placed:\n" +  formatJSON(data);
        }
        else
        {
            return "Error. Failed to cancel order:\n" + formatJSON(data);
        }
    }

    private String sendPublicRequest(String endPoint)
    {
        String urlHost = "www.bitstamp.net";
        String urlPath = endPoint;

        try
        {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + urlHost + urlPath))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200)
            {
                System.out.println("Error got response code " + response.statusCode());
                throw new RuntimeException("Status code not 200");
            }

            String resp = response.body();
            return resp;
        } catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private String sendPrivateRequest(String endPoint)
    {
        String apiKey = String.format("%s %s", "BITSTAMP", this.apiKey);
        String apiKeySecret = this.apiKeySecret;
        String httpVerb = "POST";
        String urlHost = "www.bitstamp.net";
        String urlPath = endPoint;
        String urlQuery = "";
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString();
        String contentType = "application/x-www-form-urlencoded";
        String version = "v2";
        String payloadString = "offset=1";
        String signature = apiKey + httpVerb + urlHost + urlPath + urlQuery + contentType + nonce + timestamp + version
                + payloadString;

        try
        {
            SecretKeySpec secretKey = new SecretKeySpec(apiKeySecret.getBytes(), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(signature.getBytes());
            signature = new String(Hex.encodeHex(rawHmac)).toUpperCase();

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + urlHost + urlPath))
                    .POST(HttpRequest.BodyPublishers.ofString(payloadString))
                    .setHeader("X-Auth", apiKey)
                    .setHeader("X-Auth-Signature", signature)
                    .setHeader("X-Auth-Nonce", nonce)
                    .setHeader("X-Auth-Timestamp", timestamp)
                    .setHeader("X-Auth-Version", version)
                    .setHeader("Content-Type", contentType)
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200)
            {
                System.out.println("Error got response code " + response.statusCode());
                throw new RuntimeException("Status code not 200");
            }

            String serverSignature = response.headers().map().get("x-server-auth-signature").get(0);
            String responseContentType = response.headers().map().get("Content-Type").get(0);
            String stringToSign = nonce + timestamp + responseContentType + response.body();

            mac.init(secretKey);
            byte[] rawHmacServerCheck = mac.doFinal(stringToSign.getBytes());
            String newSignature = new String(Hex.encodeHex(rawHmacServerCheck));

            if (!newSignature.equals(serverSignature))
            {
                throw new RuntimeException("Signatures do not match");
            }

            return response.body();
        } catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private String sendPrivateRequest(String endPoint, String[] params)
    {
        String apiKey = String.format("%s %s", "BITSTAMP", this.apiKey);
        String apiKeySecret = this.apiKeySecret;
        String httpVerb = "POST";
        String urlHost = "www.bitstamp.net";
        String urlPath = endPoint;
        String urlQuery = "";
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString();
        String contentType = "application/x-www-form-urlencoded";
        String version = "v2";

        String payloadString = "offset=1";
        for (String param : params)
        {
            payloadString += "&" + param;
        }

        String signature = apiKey + httpVerb + urlHost + urlPath + urlQuery + contentType + nonce + timestamp + version
                + payloadString;

        try
        {
            SecretKeySpec secretKey = new SecretKeySpec(apiKeySecret.getBytes(), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(signature.getBytes());
            signature = new String(Hex.encodeHex(rawHmac)).toUpperCase();

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + urlHost + urlPath))
                    .POST(HttpRequest.BodyPublishers.ofString(payloadString))
                    .setHeader("X-Auth", apiKey)
                    .setHeader("X-Auth-Signature", signature)
                    .setHeader("X-Auth-Nonce", nonce)
                    .setHeader("X-Auth-Timestamp", timestamp)
                    .setHeader("X-Auth-Version", version)
                    .setHeader("Content-Type", contentType)
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200)
            {
                System.out.println("Error got response code " + response.statusCode());
                throw new RuntimeException("Status code not 200");
            }

            String serverSignature = response.headers().map().get("x-server-auth-signature").get(0);
            String responseContentType = response.headers().map().get("Content-Type").get(0);
            String stringToSign = nonce + timestamp + responseContentType + response.body();

            mac.init(secretKey);
            byte[] rawHmacServerCheck = mac.doFinal(stringToSign.getBytes());
            String newSignature = new String(Hex.encodeHex(rawHmacServerCheck));

            if (!newSignature.equals(serverSignature))
            {
                throw new RuntimeException("Signatures do not match");
            }

            return response.body();
        } catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private String formatJSON(JSONObject obj)
    {
        String s = "";
        for (String field : obj.keySet())
        {
            s += String.format("%-15s: %s\n", field, obj.get(field));
        }
        return s;
    }

    public static void main(String[] args)
    {
        AetherTrader trader = new AetherTrader();
        Scanner sc = new Scanner(System.in);
        sc.useDelimiter(System.lineSeparator());
        menu:
        while (true)
        {
            trader.showMenu();
            int choice = trader.getChoice(sc);
            
            switch (choice)
            {
                case 1:
                    System.out.println(trader.getBTCData());
                    break;
                case 2:
                    System.out.println(trader.getBalance());
                    break;
                case 3:
                    System.out.println(trader.getOpenOrders());
                    break;
                case 4:
                    System.out.println(trader.cancelOrder(sc));
                    break;
                case 5:
                    System.out.println(trader.placeSellLimitOrder(sc));
                case 0:
                    break menu;
                default:
                    System.out.println("Select a valid choice");
                    break;
            }
        }
        sc.close();
    }
}