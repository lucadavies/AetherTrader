import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimerTask;

import org.json.JSONObject;

public class TestWallet extends TimerTask
{
    BigDecimal btc_available;
    BigDecimal btc_balance;
    BigDecimal eur_available;
    BigDecimal eur_balance;

    ArrayList<JSONObject> orders = new ArrayList<JSONObject>();
    int nextOrderId = 0;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy hh:mm:ss");
    private BitstampAPIConnection conn = new BitstampAPIConnection("key", "keySecret");

    public TestWallet(BigDecimal btc, BigDecimal eur)
    {
        btc_available = btc;
        btc_balance = btc;
        eur_available = eur;
        eur_balance = eur;
    }

    /**
     * Get data on BTC/EUR trading at this instant.
     * @return JSONObject with keys "last", "high", "low", "vwap", "volume", "bid", "ask", "timestamp" and "open".
     */
    public JSONObject getBTCData()
    {
        JSONObject data = new JSONObject(conn.sendPublicRequest("/api/v2/ticker/btceur"));
        return data;
    }

    public JSONObject getBalance()
    {
        JSONObject balance = new JSONObject();
        balance.put("btc_available", btc_available);
        balance.put("btc_balance", btc_balance);
        balance.put("eur_available", eur_available);
        balance.put("eur_balance", eur_balance);
        return balance;
    }

    public JSONObject placeSellInstantOrder(BigDecimal amt)
    {
        if (!(amt.compareTo(btc_available) == 1))
        {
            JSONObject order = new JSONObject();
            JSONObject btcData = getBTCData();
            order.put("id", nextOrderId++);
            order.put("amount", amt);
            order.put("price", btcData.get("last"));
            order.put("type", 1);
            orders.add(order);
            return order;
        }
        else
        {
            JSONObject failure = new JSONObject();
            failure.put("status", "error");
            failure.put("reason", "Not enough BTC available.");
            return failure;
        }
    }

    public JSONObject placeBuyInstantOrder(double amt)
    {
        if (!(eur_available.compareTo(new BigDecimal(amt)) == -1))
        {
            JSONObject order = new JSONObject();
            JSONObject btcData = getBTCData();
            order.put("id", nextOrderId++);
            order.put("amount", amt);
            order.put("price", btcData.get("last"));
            order.put("type", 1);
            return order;
        }
        else
        {
            JSONObject failure = new JSONObject();
            failure.put("status", "error");
            failure.put("reason", "Not enough EUR available.");
            return failure;
        }
    }

    public JSONObject placeSellLimitOrder(BigDecimal amt, double price)
    {
        if (!(amt.compareTo(btc_available) == 1))
        {
            JSONObject order = new JSONObject();
            order.put("id", nextOrderId++);
            order.put("amount", amt);
            order.put("price", price);
            order.put("type", 1);
            orders.add(order);
            return order;
        }
        else
        {
            JSONObject failure = new JSONObject();
            failure.put("status", "error");
            failure.put("reason", "Not enough BTC available.");
            return failure;
        }
    }

    public JSONObject placeLimitBuyOrder(BigDecimal amt, double price)
    {
        if (!(amt.compareTo(btc_available) == 1))
        {
            JSONObject order = new JSONObject();
            order.put("id", nextOrderId++);
            order.put("amount", amt);
            order.put("price", price);
            order.put("type", 0);
            orders.add(order);
            return order;
        }
        else
        {
            JSONObject failure = new JSONObject();
            failure.put("status", "error");
            failure.put("reason", "Not enough BTC available.");
            return failure;
        }
    }

    public JSONObject cancelOrder(String id)
    {
        int index = -1;
        for (JSONObject order : orders)
        {
            if (order.getString("id").equals("id"))
            {
                index = orders.indexOf(order);
                break;
            }
        }
        if (index != -1)
        {
            
            JSONObject success = new JSONObject(orders.get(index));
            orders.remove(index);
            return success;
        }
        else
        {
            JSONObject failure = new JSONObject();
            failure.put("error", "No order with id " + id);
            return failure;
        }
    }

    public void run()
    {
        JSONObject data = getBTCData();
        ArrayList<Integer> indexes = new ArrayList<Integer>();
        for (JSONObject order : orders)
        {
            if (order.getInt("type") == 0)
            {
                if (data.getDouble("last") <= order.getDouble("price"))
                {
                    btc_available = btc_available.add(order.getBigDecimal("amount"));
                    indexes.add(orders.indexOf(order));
                }
            }
            else if (order.getInt("type") == 1)
            {
                if (data.getDouble("last") >= order.getDouble("price"))
                {
                    btc_available = btc_available.subtract(order.getBigDecimal("amount"));
                    indexes.add(orders.indexOf(order));
                } 
            }
        }
        for (int i : indexes)
        {
            System.out.println(String.format("[%s]: order %d executed at %d", dateFormat.format(new Date()), orders.get(i).get("id"), orders.get(i).get("price")));
            orders.remove(i);
        }
    }
}
