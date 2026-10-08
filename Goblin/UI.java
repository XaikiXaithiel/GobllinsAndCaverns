package Goblin;

import java.awt.Color;
import java.awt.Image;
import java.awt.Font;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

public class UI {
    JFrame screen;
    public JTextArea areaText;
    Main gm;
    public JPanel bgPanel[] = new JPanel[10];
    public JLabel bgLabel[] = new JLabel[10];
    public UI(Main gm) {
        this.gm = gm;
        // calls game screen
       gameScreen();
       // calls game backround
       backround(0, "cavern1.png");
       screen.setVisible(true);
    }

    // makes the game screen w text
    public void gameScreen() {

        // makes the game window
        screen = new JFrame();
        screen.setSize(1000,600);
        screen.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        screen.getContentPane().setBackground(Color.red);
        screen.setLayout(null);

        // makes the text area and displays text
        areaText = new JTextArea("i know you'd never let me down, but will you lift me off the ground? - BoyWithUke");
        areaText.setBounds(0,520,1000,50);
        areaText.setBackground(Color.BLUE);
        areaText.setForeground(Color.white);
        areaText.setEditable(false);
        areaText.setLineWrap(true);
        areaText.setWrapStyleWord(true);
        areaText.setFont(new Font("Book Antiqua",Font.PLAIN,15));
        screen.add(areaText);
    
    }
   
    // makes game backround, takes bg number and file name of photo
    public void backround(int bgNum, String bgFileName) {

    bgPanel[bgNum] = new JPanel();
    bgPanel[bgNum].setBounds(0, 0, 1000, 600);
    bgPanel[bgNum].setLayout(null);
    screen.add(bgPanel[bgNum]);

    bgLabel[bgNum] = new JLabel();
    bgLabel[bgNum].setBounds(0, 0, 1000, 600);

    ImageIcon bgIcon = new ImageIcon("images/" + bgFileName);
    Image bgImage = bgIcon.getImage();
    Image scaledImage = bgImage.getScaledInstance(1000, 600, Image.SCALE_SMOOTH);

    // only set the scaled image icon
    bgLabel[bgNum].setIcon(new ImageIcon(scaledImage));

    bgPanel[bgNum].add(bgLabel[bgNum]);
    }
}
