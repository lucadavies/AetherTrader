import org.apache.commons.codec.binary.Hex;
import org.json.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.io.IOError;
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
import java.util.UUID;

public class AetherTrader
{
    private String apiKey = "";
    private String apiKeySecret = "";

    private long startTime;
    private TradingState tradingState;
    private MarketState marketState;
    private JSONObject internalError;

    private enum TradingState
    {
        SHORT,
        LONG,
        HOLD_IN,
        HOLD_OUT
    }

    private enum MarketState
    {
        UUP,
        UP,
        FLAT,
        DW,
        DDW,
        UNKNOWN
    }

    public AetherTrader()
    {
        internalError = new JSONObject();
        internalError.put("error", "Internal AetherTrader Error");
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

    //#region Trading methods

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

    public String cancelOrder()
    {
        System.out.print("Order ID: ");
        String id = getUserInput();
        String[] params = new String[]
        {
            "id=" + id
        };

        if (userConfirm())
        {
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
        else
        {
            return "Operation cancelled.";
        }        
    }

    public String placeSellLimitOrder()
    {
        System.out.print("Amount (BTC): ");
        BigDecimal amt =  new BigDecimal(System.console().readLine());
        System.out.print("Price (EUR): ");
        BigDecimal price = new BigDecimal(System.console().readLine());
        String[] params = new String[]
        {
            "amount=" + amt,
            "price=" + price
        };
        JSONObject data = new JSONObject(sendPrivateRequest("/api/v2/sell/btceur/", params));
        if (!data.has("status"))
        {
            return "Success. Order placed:\n" +  formatJSON(data);
        }
        else
        {
            return "Error. Failed to place order:\n" + formatJSON(data);
        }
    }

    public String placeBuyLimitOrder()
    {
        System.out.print("Amount (BTC): ");
        BigDecimal amt = new BigDecimal(System.console().readLine());
        System.out.print("Price (EUR): ");
        BigDecimal price = new BigDecimal(System.console().readLine());
        String[] params = new String[]
        {
            "amount=" + amt,
            "price=" + price
        };
        System.out.println(String.format("Place buy limit order for %.8f BTC at €%.2f (Value: €%.2f)", amt, price, amt.multiply(price)));
        if (userConfirm())
        {
            JSONObject data = new JSONObject(sendPrivateRequest("/api/v2/buy/btceur/", params));
            if (!data.has("status"))
            {
                return "Success. Order placed:\n" +  formatJSON(data);
            }
            else
            {
                return "Error. Failed to place order:\n" + formatJSON(data);
            }
        }
        else
        {
            return "Operation cancelled.";
        }
    }

    //#endregion
    
    //#region Auto Trading methods

    public String startAuto()
    {
        System.out.println("This will all the program to begin trading automaticaly according to the in-built logic. Are you sure you want to continue?");
        if (userConfirm())
        {
            run(1);
            return "Bold move.";
        }
        else
        {
            return "Wise choice.";
        }
    }

    private void run(int hrsToRun)
    {
        int secondsToRun = hrsToRun * 60 * 60;
        startTime = System.currentTimeMillis();
        long elapsedTime = 0;
        do
        {
            try
            {
                Thread.sleep(500);
            } catch (InterruptedException e)
            {
                System.out.println("Wait interrupted. Continuing.");
            }

            switch (tradingState)
            {
                case SHORT:
                    break;
                case LONG:
                    break;
                case HOLD_IN:
                    break;
                case HOLD_OUT:
                    break;
                default:
                    break;
            }

            elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
            if (elapsedTime % 10 == 0)
            {
                System.out.println(elapsedTime + "s");
            }
        } while (elapsedTime < secondsToRun);
    }

    //#endregion

    //#region Utilities

    public void showMenu()
    {
        System.out.println("----- MENU -----");
        System.out.println("1. Get BTC Info");
        System.out.println("2. Get balance");
        System.out.println("3. Get open orders");
        System.out.println("4. Cancel order");
        System.out.println("5. Place sell limit order");
        System.out.println("6. Place buy limit order");
        System.out.println("9. Start automatic trading programme");
        System.out.println("0. Quit");
    }

    public int getChoice()
    {
        int choice = 0;
        try
        {
            choice = Integer.parseInt(getUserInput());
        }
        catch (Exception e)
        {
            choice = -1;
        }
        return choice;
    }

    private String getUserInput()
    {
        String input = "";
        try
        {
            input = System.console().readLine();
        }
        catch (IOError e)
        {
            input = "";
        }
        return input;
    }

    private boolean userConfirm()
    {
        System.out.print("Confirm? [yes/no]: ");
        String input = getUserInput();
        if (input.toLowerCase().equals("yes"))
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    private BigDecimal getBTCPrice()
    {
        JSONObject data = new JSONObject(sendPublicRequest("/api/v2/ticker/btceur"));
        return new BigDecimal(data.getString("last"));
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

    private JSONObject getOHLCData(int step, int limit)
    {
        if (!isValidOHLCStep(step) || limit > 1000 || limit < 1)
        {
            return internalError;
        }

        String[] params = new String[]
        {
            "step=" + step,
            "limit=" + limit
        };
        JSONObject OhlcData = new JSONObject(sendPublicRequest("/api/v2/ohlc/btceur/", params));
        if (!OhlcData.has("code"))
        {
            return OhlcData.getJSONObject("data");
        }
        else
        {
            return internalError;
        }
    }

    private boolean isValidOHLCStep(int step)
    {
        switch (step)
        {
            case 60:
            case 180:
            case 300:
            case 900:
            case 1800:
            case 3600:
            case 7200:
            case 14400:
            case 21600:
            case 43200:
            case 86400:
            case 259200:
                return true;
            default:
                return false;
        }
    }

    public MarketState calculateMarketState()
    {
        JSONObject data = getOHLCData(1800, 8);
        if (data.has("error"))
        {
            return MarketState.UNKNOWN;
        }
        float diff = 0;
        JSONArray vals = data.getJSONArray("ohlc");

        JSONObject v;
        float open;
        float close;
        for (int i = 0; i < vals.length(); i++)
        {
            v = vals.getJSONObject(i);
            open = v.getFloat("open");
            close = v.getFloat("close");
            diff += close - open;
        }
        
        float firstOpen = vals.getJSONObject(0).getFloat("open");
        float lastClose = vals.getJSONObject(vals.length() - 1).getFloat("close");
        float percentChange = (diff / firstOpen) * 100;
        System.out.println(String.format("BTC moved %+.2f%% in the last %d minutes.", percentChange, (1800 / 60) * 8));
        System.out.println(String.format("%.2f -> %.2f", firstOpen, lastClose));
        return null;
    }

    // private TradingState calculateTradingState()
    // {

    // }

    //#endregion

    //#region HTTP Requests

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

    private String sendPublicRequest(String endPoint, String[] params)
    {
        String urlHost = "www.bitstamp.net";
        String urlPath = endPoint;
        urlPath += "?";
        for (String param : params)
        {
            urlPath += "&" + param;
        }

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

    //#endregion

    public static void main(String[] args)
    {
        AetherTrader trader = new AetherTrader();

        menu:
        while (true)
        {
            trader.showMenu();
            int choice = trader.getChoice();
            
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
                    System.out.println(trader.cancelOrder());
                    break;
                case 5:
                    System.out.println(trader.placeSellLimitOrder());
                    break;
                case 6:
                    System.out.println(trader.placeBuyLimitOrder());
                    break;
                case 9:
                    System.out.println(trader.startAuto());
                case 10:
                    trader.calculateMarketState();
                    break;
                case 0:
                    break menu;
                default:
                    System.out.println("Select a valid choice");
                    break;
            }
        }
    }
}