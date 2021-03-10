import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicOptionPaneUI.ButtonActionListener;

import org.json.JSONObject;

import java.awt.*;
import java.awt.event.*;
import java.math.RoundingMode;
import java.util.Timer;
import java.util.TimerTask;

public class AetherTraderGUI extends TimerTask implements ActionListener
{
    private JFrame frame = new JFrame("Aether Trader");
    private JPanel panMain = new JPanel(new BorderLayout());
    private JPanel panData = new JPanel(new GridLayout(4, 2));
    private JPanel panDash = new JPanel(new FlowLayout());
    private JPanel panInstantOrders = new JPanel(new FlowLayout());
    private JPanel panTradingBot = new JPanel(new FlowLayout());
    private JPanel panLimit = new JPanel();
    private JTabbedPane tabCtl = new JTabbedPane(JTabbedPane.TOP);
    private JButton btnInstantBuy = new JButton("Inst. Buy");
    private JButton btnInstantSell = new JButton("Inst. Sell");
    private JButton btnStartAutoTrading = new JButton("Launch Trading Bot");
    private JButton btnStopAutoTrading = new JButton("Stop Trading Bot");
    private JLabel lblLast = new JLabel();
    private JLabel lblHigh = new JLabel();
    private JLabel lblLow = new JLabel();
    private JLabel lblVolume = new JLabel();
    private JLabel lblHello = new JLabel("Hello!");
    private AetherTrader trader;
    private Timer ticker;

    public AetherTraderGUI()
    {
        trader = new AetherTrader();

        
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 400);

        panMain.setBorder(new EmptyBorder(10, 10, 10, 10));
        panMain.add(new JLabel("Aether Trader Dashboard", SwingConstants.CENTER), BorderLayout.NORTH);
        panMain.add(panData, BorderLayout.WEST);        
        panMain.add(tabCtl, BorderLayout.CENTER);
        panMain.add(new JLabel("Open orders maybe?"), BorderLayout.EAST);
        
        panData.add(new JLabel("Last: "));
        panData.add(lblLast);
        panData.add(new JLabel("Low: "));
        panData.add(lblLow);
        panData.add(new JLabel("High: "));
        panData.add(lblHigh);
        panData.add(new JLabel("Volume: "));
        panData.add(lblVolume);

        tabCtl.addTab("Dashboard", panDash);
        tabCtl.addTab("Limit Order", panLimit);
        panLimit.add(lblHello);
        
        panDash.add(panInstantOrders);
        panDash.add(panTradingBot);
        
        panInstantOrders.setBorder(BorderFactory.createTitledBorder("Instant Orders"));
        panInstantOrders.add(btnInstantBuy);
        panInstantOrders.add(btnInstantSell);

        panTradingBot.setBorder(BorderFactory.createTitledBorder("Trading Bot"));
        panTradingBot.add(btnStartAutoTrading);
        panTradingBot.add(btnStopAutoTrading);
        
        btnInstantBuy.addActionListener(this);
        btnInstantSell.addActionListener(this);
        btnStartAutoTrading.addActionListener(this);
        btnStopAutoTrading.addActionListener(this);
        btnStopAutoTrading.setEnabled(false);

        ticker = new Timer("GUI Ticker");
        ticker.scheduleAtFixedRate(this, 0, 1000);

        frame.setContentPane(panMain);
        frame.setVisible(true);
    }

    public void actionPerformed(ActionEvent evt)
    {
        if (evt.getSource() instanceof JButton)
        {
            String msg = "";
            int resp;
            JButton btn = (JButton)evt.getSource();
            if (btn == btnInstantBuy)
            {
                msg = "Are you sure you want to place an instant buy order?";
                resp = JOptionPane.showConfirmDialog(frame, msg, "Place instant buy?", JOptionPane.YES_NO_OPTION);
                if (resp == JOptionPane.YES_OPTION)
                {

                }
            }
            else if (btn == btnInstantSell)
            {
                msg = "Are you sure you want to place an instant sell order?";
                resp = JOptionPane.showConfirmDialog(frame,msg, "Place instant sell?", JOptionPane.YES_NO_OPTION);
                if (resp == JOptionPane.YES_OPTION)
                {

                }
            }
            else if (btn == btnStartAutoTrading)
            {
                msg = "Are you sure you want to launch the trading bot?\nThis is still VERY MUCH  a work in progress and does not operate on your real funds.";
                resp = JOptionPane.showConfirmDialog(frame, msg, "Launch trading bot?", JOptionPane.YES_NO_OPTION);
                if (resp == JOptionPane.YES_OPTION)
                {
                    btnStartAutoTrading.setEnabled(false);
                    btnStopAutoTrading.setEnabled(true);
                    trader.startAuto();
                }
            }
            else if (btn == btnStopAutoTrading)
            {
                msg = "Are you sure you want to halt the trading bot?\n";
                resp = JOptionPane.showConfirmDialog(frame, msg, "Halt trading bot?", JOptionPane.YES_NO_OPTION);
                if (resp == JOptionPane.YES_OPTION)
                {
                    //btnStartAutoTrading.setEnabled(true);
                    btnStopAutoTrading.setEnabled(false);
                    trader.stopAuto();
                }
            }
        }
    }

    public void run()
    {
        JSONObject data = trader.getBTCData();
        lblLast.setText("€" + data.getString("last"));
        lblHigh.setText("€" + data.getString("high"));
        lblLow.setText("€" + data.getString("low"));
        lblVolume.setText(data.getBigDecimal("volume").setScale(2, RoundingMode.FLOOR).toString() + "BTC");
    }

    public static void main(String[] args)
    {
        AetherTraderGUI gui = new AetherTraderGUI();
    }
}
