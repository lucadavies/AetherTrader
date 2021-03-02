import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.management.RuntimeErrorException;

import org.apache.commons.codec.binary.Hex;
import org.json.JSONObject;

// TODO document this class at least a little bit...

public class BitstampAPIConnection
{
    private String defaultApiKeyPath = "key";
    private String defaultApiKeySecretPath = "secretKey";
    private String apiKey = null;
    private String apiKeySecret = null;

    private final HttpClient client = HttpClient.newHttpClient();

    public BitstampAPIConnection()
    {
        loadKeys(defaultApiKeyPath, defaultApiKeySecretPath);
    }

    public BitstampAPIConnection(String apiKeyPath, String apiKeySecretPath)
    {
        loadKeys(apiKeyPath, apiKeySecretPath);
    }

    private void loadKeys(String keyPath, String keySecretPath)
    {
        try
        {
            apiKey = Files.readString(Paths.get(keyPath));
            apiKeySecret = Files.readString(Paths.get(keySecretPath));
        }
        catch (IOException e)
        {
            apiKey = null;
            apiKeySecret = null;
            System.out.println("Error reading API keys - account specific functions unavailable. Please check your key files and try again.");
        }
    }
    
    public String sendPublicRequest(String endPoint)
    {
        String urlHost = "www.bitstamp.net";
        String urlPath = endPoint;

        HttpResponse<String> response = null;
        try
        {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + urlHost + urlPath))
                .GET()
                .build();

            response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200)
            {
                throw new BadResponseException(response.statusCode());

                // JSONObject errorResp = new JSONObject();
                // errorResp.put("status", "failure");
                // errorResp.put("code", response.statusCode());
                // errorResp.put("error", "status code not 200");
                // System.out.println("Error: got response code " + response.statusCode());
                // return errorResp.toString();
            }

            String resp = response.body();
            return resp;
        }
        catch (BadResponseException e)
        {
            throw new RuntimeException(e);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public String sendPublicRequest(String endPoint, String[] params)
    {
        String urlHost = "www.bitstamp.net";
        String urlPath = endPoint;
        urlPath += "?";
        for (String param : params)
        {
            urlPath += "&" + param;
        }
        
        HttpResponse<String> response = null;
        try
        {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + urlHost + urlPath))
                .GET()
                .build();

            response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200)
            {
                throw new BadResponseException(response.statusCode());
                // JSONObject errorResp = new JSONObject();
                // errorResp.put("status", "failure");
                // errorResp.put("code", response.statusCode());
                // errorResp.put("error", "status code not 200");
                // System.out.println("Error: got response code " + response.statusCode());
                // return errorResp.toString();
            }

            String resp = response.body();
            return resp;
        }
        catch (BadResponseException e)
        {
            throw new RuntimeException(e);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public String sendPrivateRequest(String endPoint)
    {
        // Check API Key and API Key Secret are present
        if (this.apiKey == null || this.apiKeySecret == null)
        {
            //throw new APIKeyMissingException();
            throw new RuntimeException("API Key missing.");

            // JSONObject errorResp = new JSONObject();
            // errorResp.put("status", "failure");
            // errorResp.put("code", "N/A");
            // errorResp.put("error", "Missing API Key or API Key Secret");
            // System.out.println("Missing API Key or API Key Secret");
            // return errorResp.toString();
        }

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
                throw new BadResponseException(response.statusCode());
                // JSONObject errorResp = new JSONObject();
                // errorResp.put("status", "failure");
                // errorResp.put("code", response.statusCode());
                // errorResp.put("error", "status code not 200");
                // System.out.println("Error: got response code " + response.statusCode());
                // return errorResp.toString();
            }

            String serverSignature = response.headers().map().get("x-server-auth-signature").get(0);
            String responseContentType = response.headers().map().get("Content-Type").get(0);
            String stringToSign = nonce + timestamp + responseContentType + response.body();

            mac.init(secretKey);
            byte[] rawHmacServerCheck = mac.doFinal(stringToSign.getBytes());
            String newSignature = new String(Hex.encodeHex(rawHmacServerCheck));

            if (!newSignature.equals(serverSignature))
            {
                throw new SignatureMismatchException();
                // JSONObject errorResp = new JSONObject();
                // errorResp.put("status", "failure");
                // errorResp.put("code", response.statusCode());
                // errorResp.put("error", "Non-matching signatures");
                // System.out.println("Request/response signatures do not match");
                // return errorResp.toString();
            }

            return response.body();
        }
        catch (SignatureMismatchException e)
        {
            throw new RuntimeException(e);
        }
        catch (BadResponseException e)
        {
            throw new RuntimeException(e);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public String sendPrivateRequest(String endPoint, String[] params)
    {
        // Check API Key and API Key Secret are present
        if (this.apiKey == null || this.apiKeySecret == null)
        {
            //throw new APIKeyMissingException();
            throw new RuntimeException("API Key missing.");

            // JSONObject errorResp = new JSONObject();
            // errorResp.put("status", "failure");
            // errorResp.put("code", "N/A");
            // errorResp.put("error", "Missing API Key or API Key Secret");
            // System.out.println("Missing API Key or API Key Secret");
            // return errorResp.toString();
        }

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
                throw new BadResponseException(response.statusCode());
                // JSONObject errorResp = new JSONObject();
                // errorResp.put("status", "failure");
                // errorResp.put("code", response.statusCode());
                // errorResp.put("error", "status code not 200");
                // System.out.println("Error: got response code " + response.statusCode());
                // return errorResp.toString();
            }

            String serverSignature = response.headers().map().get("x-server-auth-signature").get(0);
            String responseContentType = response.headers().map().get("Content-Type").get(0);
            String stringToSign = nonce + timestamp + responseContentType + response.body();

            mac.init(secretKey);
            byte[] rawHmacServerCheck = mac.doFinal(stringToSign.getBytes());
            String newSignature = new String(Hex.encodeHex(rawHmacServerCheck));

            if (!newSignature.equals(serverSignature))
            {
                throw new SignatureMismatchException();
                // JSONObject errorResp = new JSONObject();
                // errorResp.put("status", "failure");
                // errorResp.put("code", response.statusCode());
                // errorResp.put("error", "Non-matching signatures");
                // System.out.println("Request/response signatures do not match");
                // return errorResp.toString();
            }

            return response.body();
        }
        catch (SignatureMismatchException e)
        {
            throw new RuntimeException(e);
        }
        catch (BadResponseException e)
        {
            throw new RuntimeException(e);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
