import javax.swing.*;
import javax.swing.border.EmptyBorder;

import org.json.JSONObject;

import java.awt.*;
import java.awt.event.*;
import java.math.RoundingMode;
import java.util.Timer;
import java.util.TimerTask;

public class AetherTraderGUI extends TimerTask implements ActionListener
{
    private JFrame frame;
    private JPanel panMain;
    private JPanel panData;
    private JButton btnTest;
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

        frame = new JFrame("Aether Trader");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 400);

        panMain = new JPanel(new BorderLayout());
        panData = new JPanel(new GridLayout(4, 2));
        btnTest = new JButton("Test");
        btnTest.addActionListener(this);

        panMain.add(panData, BorderLayout.WEST);
        panMain.setBorder(new EmptyBorder(10, 10, 10, 10));

        panData.add(new JLabel("Last: "));
        panData.add(lblLast);
        panData.add(new JLabel("Low: "));
        panData.add(lblLow);
        panData.add(new JLabel("High: "));
        panData.add(lblHigh);
        panData.add(new JLabel("Volume: "));
        panData.add(lblVolume);

        panMain.add(new JLabel("Aether Trader Dashboard", SwingConstants.CENTER), BorderLayout.NORTH);
        panMain.add(new JButton(), BorderLayout.CENTER);
        panMain.add(new JButton(), BorderLayout.EAST);
        panMain.add(btnTest, BorderLayout.SOUTH);
        
        ticker = new Timer();
        ticker.scheduleAtFixedRate(this, 0, 1000);

        frame.setContentPane(panMain);
        frame.setVisible(true);
    }

    public void actionPerformed(ActionEvent evt)
    {
        if (evt.getSource() instanceof JButton)
        {
            JButton btn = (JButton)evt.getSource();
            if (btn == btnTest);
            {
                lblHello.setText("clickity clack");
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
