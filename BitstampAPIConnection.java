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

import org.apache.commons.codec.binary.Hex;

/**
 * Provides a facility to send both private and public API calls to Bitstamp. There is no persistent connection, each 
 * call a seperate connection, though the <code>HttpClient</code> instance persists.
 */
public class BitstampAPIConnection
{
    private String defaultApiKeyPath = "key";
    private String defaultApiKeySecretPath = "secretKey";
    private String apiKey = null;
    private String apiKeySecret = null;

    private final int MAX_RETRY = 3;
    private final HttpClient client = HttpClient.newHttpClient();

    /**
     * Creates a new BitstampAPIConnection instance, attempting to load keys from default locations relative to the 
     * execution location: "key" and "secretKey", (No file extension).
     */
    public BitstampAPIConnection()
    {
        loadKeys(defaultApiKeyPath, defaultApiKeySecretPath);
    }

    /**
     * Creates a new BitstampAPIConnection instance, attempting to load keys from the provided locations.
     *  
     * @param apiKeyPath path to load API Key from
     * @param apiKeySecretPath path to load the API Key Scret from
     */
    public BitstampAPIConnection(String apiKeyPath, String apiKeySecretPath)
    {
        loadKeys(apiKeyPath, apiKeySecretPath);
    }

    /**
     * Loads keys into this instance using the provided paths.
     * 
     * @param keyPath path to load API Key from
     * @param keySecretPath path to load the API Key Scret from
     */
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
    
    /**
     * Send an API call to a public endpoint on Bitstamp's API.
     * 
     * @param endPoint the endpoint to call
     * @return the API endpoint response
     */
    public String sendPublicRequest(String endPoint)
    {
        String urlHost = "www.bitstamp.net";
        String urlPath = endPoint;

        HttpResponse<String> response = null;
        int i = 0;
        while (true)
        {
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
                }

                String resp = response.body();
                return resp;
            }
            catch (BadResponseException e)
            {
                if (i++ < MAX_RETRY)
                {
                    System.out.println("[API Connection]: Server returned bad response. Retrying...");
                    continue;
                }
                throw new RuntimeException(e);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
        
    }

    /**
     * Send an API call to a public endpoint on Bitstamp's API.
     * 
     * @param endPoint the endpoint to call
     * @param params the parameters to send with the request
     * @return the API endpoint response
     */
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

        int i = 0;
        while (true)
        {
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
                }
    
                String resp = response.body();
                return resp;
            }
            catch (BadResponseException e)
            {
                if (i++ < MAX_RETRY)
                {
                    System.out.println("[API Connection]: Server returned bad response. Retrying...");
                    continue;
                }
                throw new RuntimeException(e);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Send an API call to a public endpoint on Bitstamp's API.
     * 
     * @param endPoint the endpoint to call
     * @return the API endpoint response
     */
    public String sendPrivateRequest(String endPoint)
    {
        // Check API Key and API Key Secret are present
        if (this.apiKey == null || this.apiKeySecret == null)
        {
            throw new RuntimeException(new APIKeyMissingException());
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
        String signature = apiKey + httpVerb + urlHost + urlPath + urlQuery + contentType + nonce + timestamp + version + payloadString;

        int i = 0;
        while (true)
        {
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
                }
    
                return response.body();
            }
            catch (SignatureMismatchException e)
            {
                if (i++ < MAX_RETRY)
                {
                    System.out.println("[API Connection]: " + e.getMessage() + " Retrying...");
                    continue;
                }
                throw new RuntimeException(e);
            }
            catch (BadResponseException e)
            {
                if (i++ < MAX_RETRY)
                {
                    System.out.println("[API Connection]: " + e.getMessage() + " Retrying...");
                    continue;
                }
                throw new RuntimeException(e);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Send an API call to a public endpoint on Bitstamp's API.
     * 
     * @param endPoint the endpoint to call
     * @param params the parameters to send with the request
     * @return the API endpoint response
     */
    public String sendPrivateRequest(String endPoint, String[] params)
    {
        // Check API Key and API Key Secret are present
        if (this.apiKey == null || this.apiKeySecret == null)
        {
            throw new RuntimeException(new APIKeyMissingException());
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

        String signature = apiKey + httpVerb + urlHost + urlPath + urlQuery + contentType + nonce + timestamp + version + payloadString;

        int i = 0;
        while (true)
        {
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
                }

                return response.body();
            }
            catch (SignatureMismatchException e)
            {
                if (i++ < MAX_RETRY)
                {
                    System.out.println("[API Connection]: " + e.getMessage() + " Retrying...");
                    continue;
                }
                throw new RuntimeException(e);
            }
            catch (BadResponseException e)
            {
                if (i++ < MAX_RETRY)
                {
                    System.out.println("[API Connection]: " + e.getMessage() + " Retrying...");
                    continue;
                }
                throw new RuntimeException(e);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }
}
