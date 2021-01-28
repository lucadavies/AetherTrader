import java.io.IOError;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/*  TODO ensure implementation of lastTransactionPrice (what happens on order cancel?)
    TODO Write logic for decision making
        use fixed profit margins: 
            HOLD_IN -> LONG caused by placing limit sell ~1.5% above last transaction price
            HOLD_OUT -> SHORT caused by placing limit buy ~1.5% below last transaction price
        use fixed loss-control margins:
            LONG -> HOLD_IN caused by price dropping more than ~2% below last transaction price
            SHORT -> HOLD_OUT casued by price rising more than ~2% above last transaction price
    TODO Create framework for dry testing (save file with BTC holding amount?)
    TODO Consider swapping timing logic to using Timer / TimerTask
*/


public class AetherTrader
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

    private enum MarketState
    {
        /** Up > 5% */
        VOLATILE_UP,
        /** Up 2.5% - 5% */
        UUP,
        /** Up 0.25% - 2.5% */
        UP,
        /** Between -0.25% and +0.25% */
        FLAT,
        /** Down  0.25% - 2.5% */
        DW,
        /** Down 2.5% - 5% */
        DDW,
        /** Down > 5% */
        VOLATILE_DW,
        /** An error caused a failure to measure market state. */
        UNKNOWN
    }

    private enum Trend
    {
        UP,
        FLAT,
        DOWN
    }

    private BitstampAPIConnection conn = new BitstampAPIConnection("key", "keySecret");
    private long startTime;
    private TradingState tradingState = TradingState.HOLD_IN;
    private MarketState marketState = MarketState.UNKNOWN;
    private double lastTransactionPrice;
    private CircularList<MarketState> marketHistory = new CircularList<MarketState>(5);
    private JSONObject internalError;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy hh:mm:ss");
    private final double PROFIT_MARGIN = 0.015;

    public AetherTrader()
    {
        internalError = new JSONObject();
        internalError.put("error", "Internal AetherTrader Error");
    }

    //#region Trading methods

    /**
     * Get data on BTC/EUR trading at this instant.
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
     * Returns an array of open orders on account. Each represented as a JSONObject with keys:
     * "datetime", "amount", "currecny_pair", "price", "id" and "type".
     * @return JSONArray containing JSONObjects representing orders.
     */
    public JSONArray getOpenOrders()
    {
        JSONArray data = new JSONArray(conn.sendPrivateRequest("/api/v2/open_orders/all/"));
        
        if (data.length() != 0)
        {
            return data;
        }
        else
        {
            return null;
        }
    }

    /**
     * Cancels order with ID prompted for at command line.
     * @return JSONObject representing the cancelled order.
     */
    private JSONObject cancelOrder(String id)
    {
        String[] params = new String[]
        {
            "id=" + id
        };
        // TODO getOrder() to confirm via given order info
        if (userConfirm())
        {
            JSONObject data = new JSONObject(conn.sendPrivateRequest("/api/v2/cancel_order/", params));
            if (!data.has("error"))
            {
                lastTransactionPrice = -1;
                data.put("status", "success");
                return data;
            }
            else
            {
                data.put("status", "failure");
                return data;
            }
        }
        else
        {
            return null;
        }        
    }

    /**
     * Cancels order with ID prompted for at command line.
     * @return JSONObject representing the cancelled order
     */
    public JSONObject userCancelOrder()
    {
        String id = getUserInput("Order ID: ");
        return cancelOrder(id);
    }

    /**
     * Places a sell limit order.
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
        System.out.println(String.format("Place sell limit order for %.8f BTC at €%.2f (Value: €%.2f)", amt, price, amt.multiply(new BigDecimal(price))));
        if (userConfirm())
        {
            JSONObject data = new JSONObject(conn.sendPrivateRequest("/api/v2/sell/btceur/", params));
            if (!data.has("status"))
            {
                lastTransactionPrice = price;
                data.put("status", "success");
                return data;
            }
            else
            {
                data.put("status", "failure");
                return data;
            }
        }
        else
        {
            return null;
        }
    }

    /**
     * Place a limit sell order of amount/at price prompted for at command line.
     * @return JSONObject repesenting the placed order.
     */
    public JSONObject userSellLimitOrder()
    {
        BigDecimal amt =  new BigDecimal(getUserInput("Amount (BTC): "));
        double price = Double.parseDouble(getUserInput("Price (EUR): "));
        return placeSellLimitOrder(amt, price);
    }

    /**
     * Places a buy limit order.
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
        System.out.println(String.format("Place buy limit order for %.8f BTC at €%.2f (Value: €%.2f)", amt, price, amt.multiply(new BigDecimal(price))));
        if (userConfirm())
        {
            JSONObject data = new JSONObject(conn.sendPrivateRequest("/api/v2/buy/btceur/", params));
            if (!data.has("status"))
            {
                lastTransactionPrice = price;
                data.put("status", "success");
                return data;
            }
            else
            {
                data.put("status", "failure");
                return data;
            }
        }
        else
        {
            return null;
        }
    }

    /**
     * Place a limit buy order of amount/at price prompted for at command line.
     * @return JSONObject repesenting the placed order.
     */
    public JSONObject userBuyLimitOrder()
    {
        BigDecimal amt =  new BigDecimal(getUserInput("Amount (BTC): "));
        double price = Double.parseDouble(getUserInput("Price (EUR): "));
        return placeBuyLimitOrder(amt, price);
    }

    //#endregion
    
    //#region Auto Trading methods

    public String startAuto()
    {
        System.out.println("This will allow the program to begin trading automaticaly according to the in-built logic. Are you sure you want to continue?");
        if (userConfirm())
        {
            run(0.5);
            return "Bold move.";
        }
        else
        {
            return "Wise choice.";
        }
    }

    private void run(double hrsToRun)
    {
        double secondsToRun = hrsToRun * 60 * 60;
        startTime = System.currentTimeMillis();
        long elapsedTime = 0;
        do
        {
            elapsedTime = (System.currentTimeMillis() - startTime) / 1000L;
            
            //get market state now
            if (elapsedTime % 60L == 0) // do this every 60 seconds
            {
                float percentChange = calculatePercentChange();
                marketState = getMarketState(percentChange);
                marketHistory.push(marketState);
                System.out.println(String.format("[%s] %-4ss: %s (%+.2f%%)", dateFormat.format(new Date()), elapsedTime, marketState, percentChange));
            }

            // TODO decide what to do
            tradingState = doAction();

            //wait
            try
            {
                Thread.sleep(500);
            } catch (InterruptedException e)
            {
                System.out.println("Wait interrupted. Continuing.");
            }
        } while (elapsedTime < secondsToRun);
    }

    /**
     * Examines current trading and market state to decide next action. Executes next action if
     * applicable and returns new trading state
     * @return result trading state
     */
    private TradingState doAction()
    {
        JSONObject btcData;
        JSONObject bal;
        switch (tradingState)
        {
            case HOLD_IN:
                if (predictMarket() == Trend.UP)
                {
                    bal = getBalance();
                    placeSellLimitOrder(bal.getBigDecimal("btc_available"), lastTransactionPrice * (1 + PROFIT_MARGIN));
                }
                break;
            case LONG:
                btcData = getBTCData();
                if (predictMarket() == Trend.DOWN || lastTransactionPrice - btcData.getDouble("last") > lastTransactionPrice * (1 - PROFIT_MARGIN))
                {
                    // TODO Plan of action in this scenario? Sell (safe) or assume rebound? (risky)
                }
                break;
            case HOLD_OUT:
                if (predictMarket() == Trend.DOWN)
                {
                    bal = getBalance();
                    placeSellLimitOrder(bal.getBigDecimal("btc_available"), lastTransactionPrice * (1 - PROFIT_MARGIN));
                }
                break;
            case SHORT:
                btcData = getBTCData();
                if (predictMarket() == Trend.UP || btcData.getDouble("last") - lastTransactionPrice > lastTransactionPrice * (1 + PROFIT_MARGIN))
                {
                    // TODO Plan of action in this scenario? Retain value outside (safe), or jump back in anticipating rise? (risk)
                }
                break;
            default:
                break;
        }
        return TradingState.UNKNOWN;
    }

    private Trend predictMarket()
    {
        // TODO look at marketHistory
        return null;
    }

    //#endregion

    //#region Trading Utilities

    public double getBTCPrice()
    {
        JSONObject data = new JSONObject(conn.sendPublicRequest("/api/v2/ticker/btceur"));
        return (data.getDouble("last"));
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
        JSONObject OhlcData = new JSONObject(conn.sendPublicRequest("/api/v2/ohlc/btceur/", params));
        if (!OhlcData.has("code"))
        {
            return OhlcData.getJSONObject("data");
        }
        else
        {
            return internalError;
        }
    }

    private float calculatePercentChange()
    {
        JSONObject data = getOHLCData(60, 60); //one minute interval, for one hour back
        if (data.has("error"))
        {
            //throw new Exception();
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
        //System.out.println(String.format("BTC moved %+.2f%% in the last %d minutes.", percentChange, (1800 / 60) * 8));
        //System.out.println(String.format("%.2f -> %.2f", firstOpen, lastClose));
        return percentChange;
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

    private MarketState getMarketState(float percent)
    {
        if (percent < 0.25 && percent > -0.25) //too small to consider
        {
            return MarketState.FLAT;
        }
        else
        {
            if (percent > 0)  //upward movement
            {
                if (percent < 1.5)
                {
                    return MarketState.UP;  // UP 0.25 - 1.5%
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
                    return MarketState.DW;  // DOWN 0.25 - 1.5%
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

    private TradingState getTradingState()
    {
        JSONObject balance = getBalance();
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
            choice = Integer.parseInt(getUserInput("Action: "));
        }
        catch (Exception e)
        {
            choice = -1;
        }
        return choice;
    }

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

    private boolean userConfirm()
    {
        String input = getUserInput("Confirm? [yes/no]: ");
        if (input.toLowerCase().equals("yes"))
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    private static String formatJSON(JSONObject obj)
    {
        String s = "";
        for (String field : obj.keySet())
        {
            s += String.format("%-15s: %s\n", field, obj.get(field));
        }
        return s;
    }

    private static String formatJSONArray(JSONArray obj)
    {
        String s = "";
        for (Object o : obj)
        {
            s += formatJSON((JSONObject)o) + "\n";
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
                    JSONArray orders = trader.getOpenOrders();
                    if (orders == null)
                    {
                        System.out.println("No orders to show.");
                    }
                    else
                    {
                        System.out.println("Open orders:");
                        System.out.println(formatJSONArray(orders));
                    }
                    break;
                case 4:
                    JSONObject cOrder = trader.userCancelOrder();
                    if (cOrder == null)
                    {
                        System.out.println("Operation cancelled.");
                    }
                    else if (cOrder.getString("status").equals("failure"))
                    {
                        System.out.println("WARNING: order not cancelled.");
                        System.out.println(formatJSON(cOrder));
                    }
                    else
                    {
                        System.out.println("Success, order cancelled.");
                        System.out.println(formatJSON(cOrder));
                    }
                    break;
                case 5:
                    JSONObject sellOrder = trader.userSellLimitOrder();
                    if (sellOrder == null)
                    {
                        System.out.println("Operation cancelled.");
                    }
                    else if (sellOrder.getString("status").equals("failure"))
                    {
                        System.out.println("Error placing order.");
                        System.out.println(formatJSON(sellOrder));
                    }
                    else
                    {
                        System.out.println("Success, sell limit order placed:");
                        System.out.println(formatJSON(sellOrder));
                    }
                    break;
                case 6:
                    JSONObject buyOrder = trader.userBuyLimitOrder();
                    if (buyOrder == null)
                    {
                        System.out.println("Operation cancelled.");
                    }
                    else if (buyOrder.getString("status").equals("failure"))
                    {
                        System.out.println("Error placing order.");
                        System.out.println(formatJSON(buyOrder));
                    }
                    else
                    {
                        System.out.println("Success, sell limit order placed:");
                        System.out.println(formatJSON(buyOrder));
                    }
                    break;
                case 9:
                    System.out.println(trader.startAuto());
                    break;
                case 10:
                    System.out.println(trader.getTradingState());
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