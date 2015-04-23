package developer.image;

import javax.swing.*;
import java.awt.*;

/**
 * Created by colin on 21/04/15.
 */
public class openCVTestPanel extends JFrame{

    private JPanel contentPane;
    static JPanel panel = new JPanel();
    static JLabel lblNewLabel = new JLabel("");

    /**
     * Launch the application.
     */
    public static void main(String[] args) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    openCVTestPanel frame = new openCVTestPanel();
                    frame.setVisible(true);

                    OpenCVMotionDetect ocvmd = new OpenCVMotionDetect();
                    lblNewLabel.setIcon(new ImageIcon(ocvmd.getImage()));


                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }


    /**
     * Create the frame.
     */
    public openCVTestPanel() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(0, 0, 750, 500);
        contentPane = new JPanel();
        setContentPane(contentPane);
        contentPane.setLayout(null);

        panel.setBounds(0, 0, 640, 480);
        contentPane.add(panel);
        lblNewLabel.setBounds(0, 0, 640, 480);
        panel.add(lblNewLabel);

    }
}
