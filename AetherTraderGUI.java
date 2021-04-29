import javax.swing.*;

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
    private JPanel panCentreContainer = new JPanel(new BorderLayout());
    private JPanel panDash = new JPanel(new FlowLayout());
    private JPanel panInstantOrders = new JPanel(new FlowLayout());
    private JPanel panBalance = new JPanel(new GridLayout(5, 2));
    private JPanel panTradingBot = new JPanel(new GridLayout(1, 2, 5, 5));
    private JPanel panNumberBox = new JPanel(new FlowLayout());
    private JPanel panLimit = new JPanel();
    private JTabbedPane tabCtl = new JTabbedPane(JTabbedPane.TOP);
    private JButton btnInstantBuy = new JButton("Inst. Buy");
    private JButton btnInstantSell = new JButton("Inst. Sell");
    private JButton btnPlus = new JButton("+");
    private JButton btnMinus = new JButton("-");
    private JButton btnStartAutoTrading = new JButton("Launch");
    private JButton btnStopAutoTrading = new JButton("Halt");
    private JTextField txtPrice = new JTextField();
    private JLabel lblTitle = new JLabel("Aether Trader Dashboard", SwingConstants.CENTER);
    private JLabel lblLast = new JLabel();
    private JLabel lblHigh = new JLabel();
    private JLabel lblLow = new JLabel();
    private JLabel lblVolume = new JLabel();
    private JLabel lblEurAval = new JLabel();
    private JLabel lblEurBal = new JLabel();
    private JLabel lblBtcAval = new JLabel();
    private JLabel lblBtcBal = new JLabel();
    private JLabel lblTotalValAval = new JLabel();
    private JLabel lblHello = new JLabel("Hello!");
    private AetherTrader trader;
    private Timer ticker;

    public AetherTraderGUI()
    {
        trader = new AetherTrader();

        // Used for custom exit behaviour
        WindowListener wl = new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent windowEvent)
            {
                String msg = "Auto trader is still running. Are you sure you want to exit?";
                if (!trader.isAutoTrading() || JOptionPane.showConfirmDialog(frame, msg, "Close Window?", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
                {
                    System.exit(0);
                }
            }
        };
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(600, 400);
        frame.addWindowListener(wl);

        panMain.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panMain.add(lblTitle, BorderLayout.NORTH);
        panMain.add(panData, BorderLayout.WEST);        
        panMain.add(panCentreContainer, BorderLayout.CENTER);
        panMain.add(panBalance, BorderLayout.EAST);

        lblTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        
        panData.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 10));
        panData.add(new JLabel("Last: "));
        panData.add(lblLast);
        panData.add(new JLabel("Low: "));
        panData.add(lblLow);
        panData.add(new JLabel("High: "));
        panData.add(lblHigh);
        panData.add(new JLabel("Volume: "));
        panData.add(lblVolume);

        panCentreContainer.add(tabCtl, BorderLayout.CENTER);
        panCentreContainer.add(panTradingBot, BorderLayout.SOUTH);

        tabCtl.addTab("Dashboard", panDash);
        tabCtl.addTab("Limit Order", panLimit);
        panLimit.add(lblHello);

        panDash.add(panInstantOrders);
        panDash.add(panNumberBox);

        panNumberBox.add(btnMinus);
        panNumberBox.add(txtPrice);
        panNumberBox.add(btnPlus);

        panInstantOrders.setBorder(BorderFactory.createTitledBorder("Instant Orders"));
        panInstantOrders.add(btnInstantBuy);
        panInstantOrders.add(btnInstantSell);

        panTradingBot.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Trading Bot"), BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        panTradingBot.add(btnStartAutoTrading);
        panTradingBot.add(btnStopAutoTrading);

        panBalance.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 0));
        panBalance.add(new JLabel("EUR Available: "));
        panBalance.add(lblEurAval);
        panBalance.add(new JLabel("BTC Available: "));
        panBalance.add(lblBtcAval);
        panBalance.add(new JLabel("EUR Balance: "));
        panBalance.add(lblEurBal);
        panBalance.add(new JLabel("BTC Balance: "));
        panBalance.add(lblBtcBal);
        panBalance.add(new JLabel("Total Value: "));
        panBalance.add(lblTotalValAval);

        JSONObject balanceData = trader.getBalance();
        lblEurAval.setText("€" + balanceData.getString("eur_available"));
        lblBtcAval.setText("€" + balanceData.getString("btc_available"));
        lblEurBal.setText("€" + balanceData.getString("eur_balance"));
        lblBtcBal.setText("€" + balanceData.getString("btc_balance"));
        lblTotalValAval.setText("€" + balanceData.getBigDecimal("value").setScale(2, RoundingMode.FLOOR).toString());
        
        btnInstantBuy.addActionListener(this);
        btnInstantSell.addActionListener(this);
        btnStartAutoTrading.addActionListener(this);
        btnStopAutoTrading.addActionListener(this);
        btnStopAutoTrading.setEnabled(false);
        btnMinus.addActionListener(this);
        btnPlus.addActionListener(this);
        txtPrice.addActionListener(this);

        txtPrice.setPreferredSize(new Dimension(80, 25));

        ticker = new Timer("GUI Ticker");
        ticker.scheduleAtFixedRate(this, 0, 10000);

        frame.setContentPane(panMain);
        frame.setVisible(true);
    }

    public void actionPerformed(ActionEvent evt)
    {
        if (evt.getSource() instanceof JButton)
        {
            btnClicked((JButton)evt.getSource(), evt);
        }
        else if (evt.getSource() instanceof JTextField)
        {
            // TODO
        }
    }

    private void btnClicked(JButton sender, ActionEvent e)
    {
        String msg = "";
        int resp;
        if (sender == btnInstantBuy)
        {
            msg = "Are you sure you want to place an instant buy order?";
            resp = JOptionPane.showConfirmDialog(frame, msg, "Place instant buy?", JOptionPane.YES_NO_OPTION);
            if (resp == JOptionPane.YES_OPTION)
            {

            }
        }
        else if (sender == btnInstantSell)
        {
            msg = "Are you sure you want to place an instant sell order?";
            resp = JOptionPane.showConfirmDialog(frame,msg, "Place instant sell?", JOptionPane.YES_NO_OPTION);
            if (resp == JOptionPane.YES_OPTION)
            {

            }
        }
        else if (sender == btnStartAutoTrading)
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
        else if (sender == btnStopAutoTrading)
        {
            msg = "Are you sure you want to halt the trading bot?\n";
            resp = JOptionPane.showConfirmDialog(frame, msg, "Halt trading bot?", JOptionPane.YES_NO_OPTION);
            if (resp == JOptionPane.YES_OPTION)
            {
                btnStartAutoTrading.setEnabled(true);
                btnStopAutoTrading.setEnabled(false);
                trader.stopAuto();
            }
        }
        else if (sender == btnMinus)
        {
            // TODO
        }
        else if (sender == btnPlus)
        {
            // TODO
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
        new AetherTraderGUI();
    }
}
