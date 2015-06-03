package developer.image;

import oculusPrime.Util;
import org.opencv.core.Core;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;

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


        // do stuff
        System.loadLibrary( Core.NATIVE_LIBRARY_NAME ); // moved to Application so only loaded once

        new Thread(new Runnable() {
            public void run() {
                try {
                    OpenCVMotionDetect ocvmd = new OpenCVMotionDetect();
//                    Util.delay(2000);
                    ocvmd.motionDetectGoTest();

                    while(true) {
                        if (ocvmd.imageupdated) {
                            lblNewLabel.setIcon(new ImageIcon(OpenCVUtils.matToBufferedImage(ocvmd.detect)));
                            ocvmd.imageupdated = false;
                        }
                        Util.delay(10);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }
}
