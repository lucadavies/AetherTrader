import java.io.IOError;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONArray;
import org.json.JSONObject;

/*  TODO ensure implementation of lastTransactionPrice (what happens on order cancel?)

    TODO Check calculatePercentagChange (data doesn't seem to match charts at all)
    TODO Write logic for decision: predictMarket()
    TODO Create framework for dry testing (save file with BTC holding amount?)
*/


public class AetherTrader extends TimerTask
{
    /**
     * Represents possible status of BTC holding.
     * Expected progression: HOLD_IN -> LONG -> HOLD_OUT -> SHORT -> [repeat].
     */
    private enum TradingState
    {
        /** Value in market. Waiting for sell indications. */
        HOLD_IN,
        /** Value in market. Waiting to sell high (limit sell placed). */
        LONG,
        /** Value out of market. Waiting for buy indications. */
        HOLD_OUT,
        /** Value out of market. Waiting to buy low (limit buy placed). */
        SHORT,
        /** Value of account is split between BTC/EUR (ratio more even than 95%/5%). */
        UNKNOWN
    }

    /**
     * Represents the movement of the market in the short-term.
     */
    private enum MarketState
    {
        /** Up > 5% */
        VOLATILE_UP,
        /** Up 2.5% - 5% */
        UUP,
        /** Up 0.20% - 2.5% */
        UP,
        /** Between -0.20% and +0.20% */
        FLAT,
        /** Down  0.20% - 2.5% */
        DW,
        /** Down 2.5% - 5% */
        DDW,
        /** Down > 5% */
        VOLATILE_DW,
        /** An error caused a failure to measure market state. */
        UNKNOWN
    }

    /**
     * Represents the trend the market is currently following.
     */
    private enum Trend
    {
        UP,
        FLAT,
        DOWN
    }

    private BitstampAPIConnection conn = new BitstampAPIConnection("key", "keySecret");
    private TradingState tradingState = TradingState.HOLD_IN;
    private MarketState marketState = MarketState.UNKNOWN;
    private double priceAtLastTransaction = -1;
    private long lastOrderID;
    private CircularList<MarketState> marketHistory = new CircularList<MarketState>(5);
    private JSONObject internalError;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
    private final double PROFIT_MARGIN = 0.015;
    private TestWallet wallet = new TestWallet(new BigDecimal(0.00338066), new BigDecimal(0));

    public AetherTrader()
    {
        internalError = new JSONObject();
        internalError.put("error", "Internal AetherTrader Error");
    }

    //#region Trading methods

    /**
     * Get data on BTC/EUR trading at this instant.
     * 
     * @return JSONObject with keys "last", "high", "low", "vwap", "volume", "bid", "ask", "timestamp" and "open".
     */
    public JSONObject getBTCData()
    {
        JSONObject data = new JSONObject(conn.sendPublicRequest("/api/v2/ticker/btceur"));
        return data;
    }

    /**
     * Get balance of account. Gives BTC and EUR balance, available balance and BTC-EUR trading fee
     * as a percentage of trade value.
     * 
     * @return JSONObject with keys "eur_available", "eur_balance", "btc_available", "btc_balance", "btceur_fee", "value".
     */
    public JSONObject getBalance()
    {
        JSONObject data = new JSONObject(conn.sendPrivateRequest("/api/v2/balance/"));
        JSONObject btcData = new JSONObject(conn.sendPublicRequest("/api/v2/ticker/btceur"));
        BigDecimal value = data.getBigDecimal("eur_balance").add(data.getBigDecimal("btc_balance").multiply(btcData.getBigDecimal("last")));
        List<String> balKeys = Arrays.asList("eur_available", "eur_balance", "btc_available", "btc_balance", "btceur_fee");
        JSONObject result = new JSONObject();
        for (String k : balKeys)
        {
            result.put(k, data.get(k));
        }
        result.put("value", value);
        return result;
    }

    /**
     * Returns a JSONObject containing order data on account. Each represented as a JSONObject with keys:
     * "datetime", "amount", "currecny_pair", "price", "id" and "type".
     * 
     * @return JSONObject with keys "status" and "orders". Value of orders is a JSONArray containing JSONObjects
     * representing orders. If an error is encountered, returns a JSONObject
     * with keys "status" and "error".
     */
    private JSONObject getOpenOrders()
    {
        String data = conn.sendPrivateRequest("/api/v2/open_orders/all/");
        if (data.charAt(0) == '[') //array returned: success
        {
            JSONArray orders = new JSONArray(data);
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("orders", orders);
            return result;
        }
        else //(error) object returned: failure
        {
            JSONObject err = new JSONObject(data);
            err.put("status", "failure");
            err.put("error", err.getString("reason"));
            err.remove("reason");
            return err;
        }   
    }

    /**
     * Gets a formatted representation of all open orders.
     * 
     * @return A formatted string
     */
    public String userGetOpenOrders()
    {
        JSONObject orderData = getOpenOrders();
        String result = "\n";
        if (orderData.getString("status").equals("success"))
        {
            if (orderData.getJSONArray("orders").length() == 0)
            {
                result += "No orders to show.\n";
            }
            else
            {
                result += "Open orders:\n";
                result += formatJSONArray(orderData.getJSONArray("orders"));
            }
        }
        else
        {
            result += "Error:";
            result += formatJSON(orderData);
        }
        
        return result;
    }
    /**
     * Cancels an order.
     * 
     * @param id The order to cancel
     * @return JSONObject representing the cancelled order. If an error is encountered, returns a JSONObject
     * with keys "status" and "error".
     */
    private JSONObject cancelOrder(long id)
    {
        String[] params = new String[]
        {
            "id=" + id
        };
        JSONObject data = new JSONObject();
        data = new JSONObject(conn.sendPrivateRequest("/api/v2/cancel_order/", params));
        if (!data.has("error"))
        {
            priceAtLastTransaction = -1;
            lastOrderID = -1;
            data.put("status", "success");
            return data;
        } 
        data.put("status", "failure");
        return data; 
    }

    /**
     * Cancels order with ID prompted for at command line.
     * 
     * @return Status message indictating the result of the cancel operation.
     */
    public String userCancelOrder()
    {
        System.out.println();
        long id = Long.parseLong(getUserInput("Order ID: "));

        String result;
        JSONObject order = getOrder(id);
        System.out.println(formatJSON(order));
        if (order.getString("status").equals("success"))
        {
            if (userConfirm())
            {
                JSONObject cOrder = cancelOrder(id);
                if (cOrder.getString("status").equals("success"))
                {
                    result = "Success, order cancelled.\n";
                }
                else
                {
                    result = "WARNING: order not cancelled.\n";
                }
            }
            else
            {
                result = "Operation cancelled.\n";
            }
        }
        else
        {
            result = "No order with id " + id + ".";
        }
        return result;        
    }

    /**
     * Places an instant sell order.
     * 
     * @param amt Amount of BTC to sell
     * @return JSONObject reprenting the placed order. If an error is encountered, returns a JSONObject
     * with keys "status" and "error".
     */
    private JSONObject placeSellInstantOrder(BigDecimal amt)
    {
        String[] params = new String[]
        {
            "amount=" + amt,
        };
        JSONObject data = new JSONObject(conn.sendPrivateRequest("/api/v2/sell/instant/btceur/", params));
        if (!data.has("status"))
        {
            priceAtLastTransaction = data.getDouble("price");
            lastOrderID = data.getLong("id");
            data.put("status", "success");
            return data;
        }
        else
        {
            data.put("status", "failure");
            data.put("error", data.getString("reason"));
            data.remove("reason");
            return data;
        }
    }

    /**
     * Places an instant sell order of amount prompted for at command line.
     * 
     * @return Status message indicating the result of the order.
     */
    public String userSellInstantOrder()
    {
        System.out.println();
        BigDecimal amt =  new BigDecimal(getUserInput("Amount (BTC): "));

        double price = getBTCPrice();
        String result;
        System.out.println(String.format("Place sell instant order for %.8f BTC at ~€%.2f (Value: ~€%.2f)", amt, price, amt.multiply(new BigDecimal(price))));
        if (userConfirm())
        {
            JSONObject sellOrder = placeSellInstantOrder(amt);
            if (sellOrder.getString("status").equals("success"))
            {
                result = "Success, sell limit order placed:\n";
            }
            else
            {
                result = "Error placing order.\n";  
            }
            result += formatJSON(sellOrder);
        }
        else
        {
            result = "Operation cancelled.\n";
        }  
        return result;
    }

    /**
     * Places an instant buy order.
     * 
     * @param amt Amount of BTC to buy
     * @return JSONObject reprenting the placed order. If an error is encountered, returns a JSONObject
     * with keys "status" and "error".
     */
    private JSONObject placeBuyInstantOrder(BigDecimal amt)
    {
        String[] params = new String[]
        {
            "amount=" + amt,
        };
        JSONObject data = new JSONObject(conn.sendPrivateRequest("/api/v2/buy/instant/btceur/", params));
        if (!data.has("status"))
        {
            priceAtLastTransaction = data.getDouble("price");
            lastOrderID = data.getLong("id");
            data.put("status", "success");
            return data;
        }
        else
        {
            data.put("status", "failure");
            data.put("error", data.getString("reason"));
            data.remove("reason");
            return data;
        }
    }

    /**
     * Places an instant sell order of amount prompted for at command line.
     * 
     * @return Status message indicating the result of the order.
     */
    public String userBuyInstantOrder()
    {
        System.out.println();
        BigDecimal amt = new BigDecimal((getUserInput("Amount (EUR): ")));

        double price = getBTCPrice();
        String result;
        System.out.println(String.format("Place buy instant order for %.8f BTC at ~€%.2f (Value: ~%.8fBTC)", amt, price, amt.divide(new BigDecimal(price))));
        if (userConfirm())
        {
            JSONObject buyOrder = placeBuyInstantOrder(amt);
            if (buyOrder.getString("status").equals("success"))
            {
                result = "Success, sell limit order placed:\n";
                result += formatJSON(buyOrder);
            }
            else
            { 
                result = "Error placing order.\n";
                result += formatJSON(buyOrder);
            }
        }
        else
        {
            result = "Operation cancelled.\n";
        }  
        return result;
    }

    /**
     * Places a sell limit order.
     * 
     * @param amt Amount to buy (BTC)
     * @param price Price to buy at (EUR)
     * @return JSONObject repesenting the placed order.
     */
    private JSONObject placeSellLimitOrder(BigDecimal amt, double price)
    {
        String[] params = new String[]
        {
            "amount=" + amt,
            "price=" + price
        };
        JSONObject data = new JSONObject(conn.sendPrivateRequest("/api/v2/sell/btceur/", params));
        if (!data.has("status"))
        {
            priceAtLastTransaction = getBTCPrice();
            lastOrderID = data.getLong("id");
            data.put("status", "success");
            return data;
        }
        else
        {
            data.put("status", "failure");
            data.put("error", data.getString("reason"));
            data.remove("reason");
            return data;
        }
    }

    /**
     * Place a limit sell order of amount/at price prompted for at command line.
     * 
     * @return Status message indicating the result of the order.
     */
    public String userSellLimitOrder()
    {
        System.out.println();
        BigDecimal amt =  new BigDecimal(getUserInput("Amount (BTC): "));
        double price = Double.parseDouble(getUserInput("Price (EUR): "));

        String result;
        System.out.println(String.format("Place sell limit order for %.8f BTC at €%.2f (Value: €%.2f)", amt, price, amt.multiply(new BigDecimal(price))));
        if (userConfirm())
        {
            JSONObject sellOrder = placeSellLimitOrder(amt, price);
            if (sellOrder.getString("status").equals("success"))
            {
                result = "Success, sell limit order placed:\n";
                result += formatJSON(sellOrder);
            }
            else
            {
                result = "Error placing order.\n";
                result += formatJSON(sellOrder);
            }
        }
        else
        {
            result = "Operation cancelled.\n";
        }  
        return result;
    }

    /**
     * Places a buy limit order.
     * 
     * @param amt Amount to buy (BTC)
     * @param price Price to buy at (EUR)
     * @return JSONObject repesenting the placed order.
     */
    private JSONObject placeBuyLimitOrder(BigDecimal amt, double price)
    {
        String[] params = new String[]
        {
            "amount=" + amt,
            "price=" + price
        };
        JSONObject data = new JSONObject(conn.sendPrivateRequest("/api/v2/buy/btceur/", params));
        if (!data.has("status"))
        {
            priceAtLastTransaction = getBTCPrice();
            lastOrderID = data.getLong("id");
            data.put("status", "success");
            return data;
        }
        else
        {
            data.put("status", "failure");
            data.put("error", data.getString("reason"));
            data.remove("reason");
            return data;
        }
    }

    /**
     * Place a limit buy order of amount/at price prompted for at command line.
     * 
     * @return Status message indicating the result of the order.
     */
    public String userBuyLimitOrder()
    {
        System.out.println();
        BigDecimal amt =  new BigDecimal(getUserInput("Amount (BTC): "));
        double price = Double.parseDouble(getUserInput("Price (EUR): "));

        JSONObject buyOrder = placeBuyLimitOrder(amt, price);
        String result;

        System.out.println(String.format("Place buy limit order for %.8f BTC at €%.2f (Value: €%.2f)", amt, price, amt.multiply(new BigDecimal(price))));
        if (userConfirm())
        {
            if (buyOrder.getString("status").equals("success"))
            {
                result = "Success, sell limit order placed:\n";
                result += formatJSON(buyOrder);
            }
            else
            {
                result = "Error placing order.\n";
                result += formatJSON(buyOrder);
            }
        }
        else
        {
            result = "Operation cancelled.\n";
        }
        return result;
    }

    //#endregion
    
    //#region Auto Trading methods

    /**
     * Begins running thr automatic trading programme.
     * 
     * @return A comment reflecting on the user's decision to begin the programme or not. (WIP)
     */
    public String startAuto()
    {
        System.out.println("This will allow the program to begin trading automaticaly according to the in-built logic. Are you sure you want to continue?");
        if (userConfirm())
        {
            // TODO setup: get trading state, cancel current orders
            tradingState = getTradingState();
            new Timer().scheduleAtFixedRate(this, 0, 60000);
            return "Bold move.\n";
        }
        else
        {
            return "Wise choice.\n";
        }
    }

    public void run()
    {        
        //get market state now
        float percentChange = calculatePercentChange(60, 60);
        if (percentChange == -999)
        {
            return;
        }

        marketState = getMarketState(percentChange);
        marketHistory.push(marketState);
        System.out.println(String.format("[%s]: %s (%+.2f%%)", dateFormat.format(new Date()), marketState, percentChange));

        // TODO decide what to do
        tradingState = doAction();
    }

    /**
     * Examines current trading and market state to decide next action. Executes next action if
     * applicable and returns new trading state.
     * 
     * @return resultant trading state
     * @see TradingState
     */
    private TradingState doAction()
    {
        JSONObject btcData = getBTCData();
        JSONObject bal;
        if (priceAtLastTransaction == -1)
        {
            priceAtLastTransaction = btcData.getDouble("last");
        }

        switch (tradingState)
        {
            case HOLD_IN:
                if (predictMarket() == Trend.UP)
                {
                    bal = wallet.getBalance();
                    wallet.placeSellLimitOrder(bal.getBigDecimal("btc_available"), priceAtLastTransaction * (1 + PROFIT_MARGIN));
                    System.out.println(String.format("[%s]: HOLD_IN -> LONG (Limit sell placed at %.2f)", dateFormat.format(new Date()), priceAtLastTransaction * (1 + PROFIT_MARGIN)));
                    return TradingState.LONG;
                }
                break;
            case LONG:
                btcData = getBTCData();
                if (predictMarket() == Trend.DOWN || btcData.getDouble("last") > priceAtLastTransaction * (1 - PROFIT_MARGIN))
                {
                    // if last price is a whole margin below price when last order placed (wrong direction)
                    // Trend is down when in a long position
                    JSONObject cancelledOrder = wallet.cancelOrder(lastOrderID);
                    if (cancelledOrder.getString("status").equals("success"))
                    {
                        bal = wallet.getBalance();
                        JSONObject o = wallet.placeSellInstantOrder(bal.getBigDecimal("btc_available"));
                        System.out.println(String.format("[%s]: LONG -> HOLD_IN (Instant sell placed at €%.2f)", dateFormat.format(new Date()), o.getDouble("price")));
                        return TradingState.HOLD_IN;
                    }
                    else
                    {
                        System.out.println("WARNING: Unable to cancel limit sell order. Market falling while in a LONG position.");
                        System.out.println(String.format("[%s]: LONG -> LONG (Failed to cancel order)", dateFormat.format(new Date())));
                        return TradingState.LONG;
                    }
                }
                break;
            case HOLD_OUT:
                if (predictMarket() == Trend.DOWN)
                {
                    bal = wallet.getBalance();
                    wallet.placeBuyLimitOrder(bal.getBigDecimal("btc_available"), priceAtLastTransaction * (1 - PROFIT_MARGIN));
                    System.out.println(String.format("[%s]: HOLD_OUT -> SHORT (Limit buy placed at €%.2d)", dateFormat.format(new Date()), priceAtLastTransaction * (1 - PROFIT_MARGIN)));
                    return TradingState.SHORT;
                }
                break;
            case SHORT:
                btcData = getBTCData();
                if (predictMarket() == Trend.UP || btcData.getDouble("last") > priceAtLastTransaction * (1 + PROFIT_MARGIN))
                {
                    // if last price is a whole margin above price when last order placed (wrong direction)
                    // Trend is up when in a short position
                    JSONObject cancelledOrder = wallet.cancelOrder(lastOrderID);
                    if (cancelledOrder.getString("status").equals("success"))
                    {
                        bal = wallet.getBalance();
                        JSONObject o = wallet.placeBuyInstantOrder(bal.getBigDecimal("eur_available"));
                        System.out.println(String.format("[%s]: SHORT -> HOLD_OUT (Instant buy placed at €%.2d)", dateFormat.format(new Date()), o.getDouble("price")));
                        return TradingState.HOLD_OUT;
                    }
                    else
                    {
                        System.out.println("WARNING: Unable to cancel limit buy order. Market rising while in a SHORT position.");
                        System.out.println(String.format("[%s]: SHORT -> SHORT (Failed to cancel order)", dateFormat.format(new Date())));
                        return TradingState.SHORT;
                    }  
                }
                break;
            default:
                break;
        }
        return TradingState.UNKNOWN;
    }

    /**
     * Predicts the likely movement of the market based upon previous data. (HEAVYILY WIP).
     * @return The <code>Trend</code> the market will follow 
     * @see Trend
     */
    private Trend predictMarket()
    {
        if (marketHistory.contains(MarketState.UNKNOWN))
        {
            return Trend.FLAT;
        }
        
        if (marketState == MarketState.UP || marketState == MarketState.UUP)
        {
            return Trend.UP;
        }
        else if (marketState == MarketState.DW || marketState == MarketState.DDW)
        {
            return Trend.DOWN;
        }
        
        // TODO look at marketHistory
        return null;
    }

    //#endregion

    //#region Trading Utilities

    /**
     * Gets the current price of BTC in EUR.
     * 
     * @return the price
     */
    public double getBTCPrice()
    {
        JSONObject data = new JSONObject(conn.sendPublicRequest("/api/v2/ticker/btceur"));
        return (data.getDouble("last"));
    }

    /**
     * Gets a list of OHLC data. 
     * 
     * @param step Timeframe in seconds
     * @param limit Maximum number of results to return
     * @return JSONObject containing keys "status" and "data" (OHLC data)
     */
    private JSONObject getOHLCData(int step, int limit)
    {
        String[] params = new String[]
        {
            "step=" + step,
            "limit=" + limit
        };
        JSONObject ohlcData = new JSONObject(conn.sendPublicRequest("/api/v2/ohlc/btceur/", params));

        JSONObject resData = new JSONObject();
        if (!ohlcData.has("code"))
        {
            resData.put("status", "success");
            resData.put("data", ohlcData.getJSONObject("data").getJSONArray("ohlc"));
            return resData;
        }
        else
        {
            resData.put("status", "failure");
            resData.put("error", ohlcData.get("errors"));
            return resData;
        }
    }

    /**
     * Get details of an order.
     * 
     * @param id The ID of the order
     * @return JSONObject containing order details. Key "status" is "success" if order retrieved successfully, else it
     * is "failure".
     */
    private JSONObject getOrder(long id)
    {
        JSONObject orderData = getOpenOrders();
        if (orderData.getString("status").equals("success"))
        {
            JSONArray orders = orderData.getJSONArray("orders");
            for (Object o : orders)
            {
                JSONObject order = (JSONObject)o;
                if (((JSONObject)order).getLong("id") == id)
                {
                    return order;
                }
            }
            JSONObject err = new JSONObject();
            err.put("status", "failure");
            err.put("error", "No order with id " + id + ".");
            return err;
        }
        else
        {
            return orderData;
        }
        
    }

    /**
     * Calculates the percentage difference in price over a given time period.
     * 
     * @param timeStep The granularity of calculations in seconds
     * @param duration The number of <code>timeSteps</code> back to include in calculation
     * @return The percentage change, postive if up, negative if down
     */
    private float calculatePercentChange(int timeStep, int duration)
    {
        JSONObject data = getOHLCData(timeStep, duration); //one minute interval, for one hour back
        if (data.getString("status").equals("success"))
        {
            float diff = 0;
            JSONArray vals = data.getJSONArray("data");

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
            // System.out.println(String.format("BTC moved %+.2f%% in the last %d minutes.", percentChange, 60));
            // System.out.println(String.format("%.2f -> %.2f", firstOpen, lastClose));
            return percentChange;
        }
        else
        {
            return -999;
        }
    }

    /**
     * Gets the current market state from the percentage change
     * 
     * @param percent Change in market
     * @return The observed <code>MarketState</code>
     * @see MarketState
     */
    private MarketState getMarketState(float percent)
    {
        if (percent < 0.20 && percent > -0.20) //too small to consider
        {
            return MarketState.FLAT;
        }
        else
        {
            if (percent > 0)  //upward movement
            {
                if (percent < 1.5)
                {
                    return MarketState.UP;  // UP 0.20 - 1.5%
                }
                else if (percent < 5)
                {
                    return MarketState.UUP; // UP > 1.5 - 5%
                }
                else
                {
                    return MarketState.VOLATILE_UP; // UP > 5%
                }
            }
            else                    //downward movement
            {
                if (percent > -1.5)
                {
                    return MarketState.DW;  // DOWN 0.20 - 1.5%
                }
                else if (percent > -5)
                {
                    return MarketState.DDW; // DOWN > 1.5%
                }
                else
                {
                    return MarketState.VOLATILE_DW; // DOWN > 5%
                }
            }
        }
    }

    /**
     * Gets the current trading state using the balances of BTC and EUR
     * @return The <code>TradingState</code> of this account
     * @see TradingState
     */
    private TradingState getTradingState()
    {
        JSONObject balance = wallet.getBalance();
        BigDecimal eurAvail = balance.getBigDecimal("eur_available");
        BigDecimal eurBal = balance.getBigDecimal("eur_balance");
        BigDecimal btcAvail = balance.getBigDecimal("btc_available");
        BigDecimal btcBal = balance.getBigDecimal("btc_balance");
        BigDecimal value = balance.getBigDecimal("value");
        BigDecimal btcBalValue = btcBal.multiply(getBTCData().getBigDecimal("last"));

        double percentInBTC = btcBalValue.divide(value).doubleValue();
        double percentInEUR = eurBal.divide(value).doubleValue();

        //If more than value is split in a ratio more even than 95%/5%
        if (Math.abs(percentInBTC - percentInEUR) <= 0.9)
        {
            return TradingState.UNKNOWN;
        }

        //Assuming "correct" case (all funds fully IN or OUT)
        if (btcBal.compareTo(eurBal) == 1)  // More BTC held than EUR: HOLD_IN or LONG
        {
            if (btcBal.compareTo(btcAvail) == 1)    // More BTC in balance than available (open sell order present): LONG
            {
                return TradingState.LONG;
            }
            else                                    // More (equal) BTC avaiable as in balance (no open orders): HOLD_IN
            {
                return TradingState.HOLD_IN;
            }
        }
        else                                // More EUR held than BTC
        {
            if (eurBal.compareTo(eurAvail) == 1)    // More EUR in balance than available (open buy order present): SHORT
            {
                return TradingState.SHORT;
            }
            else
            {
                return TradingState.HOLD_OUT;       // More (equal) EUR available as in balacne (no open orders): HOLD_OUT
            }
        }
    }

    //#endregion

    //#region Interface Utilities

    /**
     * Displays the menu at command line.
     */
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

    /**
     * Gets the user's menu choice.
     * 
     * @return The user's choice
     */
    public int getChoice()
    {
        int choice = 0;
        try
        {
            choice = Integer.parseInt(getUserInput("Action: "));
        }
        catch (Exception e)
        {
            choice = -1;
        }
        return choice;
    }

    /**
     * Gets user input from command line.
     * 
     * @param msg Message to prompt the user with
     * @return The user's input
     */
    private String getUserInput(String msg)
    {
        String input = "";
        System.out.print(msg);
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

    /**
     * Prompts the user to confirm with a yes/no prompt.
     * 
     * @return True is user entered "yes", otherwise False
     */
    private boolean userConfirm()
    {
        String input = getUserInput("Confirm? [yes/no]: ");
        System.out.println();
        if (input.toLowerCase().equals("yes"))
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * Formats a JSONObject for printing to command line
     * 
     * @param obj JSONObject to format
     * @return Formatted string
     */
    private static String formatJSON(JSONObject obj)
    {
        String s = "\n";
        for (String field : obj.keySet())
        {
            s += String.format("%-15s: %s\n", field, obj.get(field));
        }
        return s;
    }

    /**
     * Formats a JSONArray for printing to command line
     * 
     * @param obj JSONArray to format
     * @return Formatted string
     */
    private static String formatJSONArray(JSONArray obj)
    {
        String s = "";
        for (Object o : obj)
        {
            s += formatJSON((JSONObject)o);
        }
        return s;
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
                    System.out.println(formatJSON(trader.getBTCData()));
                    break;
                case 2:
                    System.out.println(formatJSON(trader.getBalance()));
                    break;
                case 3:
                    System.out.println(trader.userGetOpenOrders());
                    break;
                case 4:
                    System.out.println(trader.userCancelOrder());
                    break;
                case 5:
                    System.out.println(trader.userSellLimitOrder());
                    break;
                case 6:
                    System.out.println(trader.userBuyLimitOrder());
                    break;
                case 9:
                    System.out.println(trader.startAuto());
                    break menu;
                case 10:
                    trader.getOHLCData(60, 60);
                    break;
                case 0:
                    break menu;
                default:
                    System.out.println("Select a valid choice");
                    break;
            }
            trader.getUserInput("Press enter to continue...");
        }
    }
}