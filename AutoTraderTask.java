import java.util.TimerTask;

public class AutoTraderTask extends TimerTask
{
    AetherTrader trader;

    public AutoTraderTask(AetherTrader trader)
    {
        this.trader = trader;
    }

    public void run()
    {
        trader.doNextAutoTrade();
    }
}
