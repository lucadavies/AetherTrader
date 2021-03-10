import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONArray;
import org.json.JSONObject;

// TODO simulate trading fee!!!!
public class TestWallet extends TimerTask
{
    BigDecimal btc_available;
    BigDecimal btc_balance;
    BigDecimal eur_available;
    BigDecimal eur_balance;

    ArrayList<JSONObject> orders = new ArrayList<JSONObject>();
    long nextOrderId = 0;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy hh:mm");
    private BitstampAPIConnection conn = new BitstampAPIConnection("key", "keySecret");
    private Timer orderProcessTimer;

    private int ordersPlaced = 0;
    private int ordersExecuted = 0;
    private int ordersCancelled = 0; 

    public TestWallet(BigDecimal btc, BigDecimal eur)
    {
        btc_available = btc;
        btc_balance = btc;
        eur_available = eur;
        eur_balance = eur;
        orderProcessTimer = new Timer("Wallet Order Processor");
        orderProcessTimer.scheduleAtFixedRate(this, 0, 60000);
    }

    /**
     * Get data on BTC/EUR trading at this instant.
     * @return JSONObject with keys "last", "high", "low", "vwap", "volume", "bid", "ask", "timestamp" and "open".
     */
    public JSONObject getBTCData() 
    {
        // TODO Obviously this needs to be better 
        JSONObject data = new JSONObject(conn.sendPublicRequest("/api/v2/ticker/btceur"));
        if (data.has("error"))
        {
            throw new RuntimeException("Bugger");
        }
        // BAD, will cause crash
        return data;
    }

    public JSONObject getBalance()
    {
        JSONObject balance = new JSONObject();
        JSONObject btcData = getBTCData();
        balance.put("btc_available", btc_available);
        balance.put("btc_balance", btc_balance);
        balance.put("eur_available", eur_available);
        balance.put("eur_balance", eur_balance);
        BigDecimal value = balance.getBigDecimal("eur_balance").add(balance.getBigDecimal("btc_balance").multiply(btcData.getBigDecimal("last")));
        BigDecimal valueBTC = balance.getBigDecimal("btc_balance").add(balance.getBigDecimal("eur_balance").divide(btcData.getBigDecimal("last"), RoundingMode.HALF_DOWN));
        balance.put("value", value);
        balance.put("value_btc", valueBTC);
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
            order.put("price", btcData.getBigDecimal("last"));
            order.put("type", 1);
            order.put("status", "success");

            btc_available = btc_available.subtract(amt);
            btc_balance = btc_balance.subtract(amt);
            eur_available = eur_available.add(amt.multiply(btcData.getBigDecimal("last")));
            eur_balance = eur_balance.add(amt.multiply(btcData.getBigDecimal("last")));

            ordersPlaced++;
            ordersExecuted++;

            return order;
        }
        else
        {
            JSONObject failure = new JSONObject();
            failure.put("status", "failure");
            failure.put("reason", "Not enough BTC available.");
            return failure;
        }
    }

    public JSONObject placeBuyInstantOrder(BigDecimal amt)
    {
        if (!(eur_available.compareTo(amt) == -1))
        {
            JSONObject order = new JSONObject();
            JSONObject btcData = getBTCData();
            order.put("id", nextOrderId++);
            order.put("amount", amt);
            order.put("price", btcData.getBigDecimal("last"));
            order.put("type", 1);
            order.put("status", "success");

            btc_available = btc_available.add(amt.divide(btcData.getBigDecimal("last"), RoundingMode.HALF_DOWN));
            btc_balance = btc_balance.add(amt.divide(btcData.getBigDecimal("last"), RoundingMode.HALF_DOWN));
            eur_available = eur_available.subtract(amt);
            eur_balance = eur_balance.subtract(amt);

            ordersPlaced++;
            ordersExecuted++;

            return order;
        }
        else
        {
            JSONObject failure = new JSONObject();
            failure.put("status", "failure");
            failure.put("error", "Not enough EUR available.");
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
            order.put("status", "success");
            orders.add(order);
            ordersPlaced++;
            btc_available = btc_available.subtract(amt);
            return order;
        }
        else
        {
            JSONObject failure = new JSONObject();
            failure.put("status", "failure");
            failure.put("reason", "Not enough BTC available.");
            return failure;
        }
    }

    public JSONObject placeBuyLimitOrder(BigDecimal amt, double price)
    {
        if (!(amt.compareTo(btc_available) == 1))
        {
            JSONObject order = new JSONObject();
            order.put("id", nextOrderId++);
            order.put("amount", amt);
            order.put("price", price);
            order.put("type", 0);
            order.put("stauts", "success");
            orders.add(order);
            ordersPlaced++;
            eur_available = eur_available.subtract(order.getBigDecimal("amount").multiply(order.getBigDecimal("price")));
            return order;
        }
        else
        {
            JSONObject failure = new JSONObject();
            failure.put("status", "failure");
            failure.put("reason", "Not enough BTC available.");
            return failure;
        }
    }

    public JSONObject cancelOrder(long id)
    {
        int index = -1;
        for (JSONObject order : orders)
        {
            if (order.getLong("id") == id)
            {
                if (order.getInt("type") == 0)
                {
                    eur_available = eur_available.add(order.getBigDecimal("amount").multiply(order.getBigDecimal("price")));
                }
                else if (order.getInt("type") == 1)
                {
                    btc_available = btc_available.add(order.getBigDecimal("amount"));
                    
                }
                index = orders.indexOf(order);
                break;
            }
        }
        if (index != -1)
        {
            
            JSONObject success = new JSONObject(orders.get(index));
            success.put("status", "success");
            orders.remove(index);
            ordersCancelled++;
            return success;
        }
        else
        {
            JSONObject failure = new JSONObject();
            failure.put("status", "failure");
            failure.put("error", "No order with id " + id);
            return failure;
        }
    }

    public JSONObject getOpenOrders()
    {
        JSONObject result = new JSONObject();        
        JSONArray jOrders = new JSONArray(orders);
        result.put("status", "success");
        result.put("orders", jOrders);
        return result;
    }

    public void run()
    {
        JSONObject data = getBTCData();
        ArrayList<Integer> indexes = new ArrayList<Integer>();
        for (JSONObject order : orders)
        {
            if (order.getInt("type") == 0) // buy
            {
                if (data.getDouble("last") <= order.getDouble("price"))
                {
                    btc_balance = btc_balance.add(order.getBigDecimal("amount"));
                    eur_balance = eur_balance.subtract(order.getBigDecimal("amount").multiply(new BigDecimal(order.getDouble("price"))));
                    indexes.add(orders.indexOf(order));
                }
            }
            else if (order.getInt("type") == 1) // sell
            {
                if (data.getDouble("last") >= order.getDouble("price"))
                {
                    btc_balance = btc_balance.subtract(order.getBigDecimal("amount"));
                    eur_balance = eur_balance.add(order.getBigDecimal("amount").multiply(new BigDecimal(order.getDouble("price"))));
                    indexes.add(orders.indexOf(order));
                } 
            }
        }
        for (int i : indexes)
        {
            System.out.println(String.format("[%s]: Order %d executed at €%.2f", dateFormat.format(new Date()), orders.get(i).getLong("id"), orders.get(i).getDouble("price")));
            orders.remove(i);
            ordersExecuted++;
        }
    }

    public void close()
    {
        if (orderProcessTimer != null)
        {
            orderProcessTimer.cancel();
            orderProcessTimer.purge();
        }
    }

    public String toString()
    {
        return String.format("{P:%2s, E:%2s, C:%2s, Value: %.8fBTC (€%.2f))}", ordersPlaced, ordersExecuted, ordersCancelled, getBalance().getBigDecimal("value_btc"), getBalance().getBigDecimal("value"));
    }
}
