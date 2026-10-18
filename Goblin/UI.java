package Goblin;

import java.awt.Color;
import java.awt.Image;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.Font;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class UI {
    JFrame screen;
    public JTextArea areaText;
    Main gm;
    public JPanel bgPanel[] = new JPanel[10];
    public JLabel bgLabel[] = new JLabel[10];
    public JButton screenMenu;

    // Calls the entire ui
    public UI(Main gm) {
        this.gm = gm;
        gameScreen();
        scene();
        screen.setVisible(true);
    }

    // Makes the game screen w text
    public void gameScreen() {
        screen = new JFrame();
        screen.setSize(1000, 600);
        screen.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        screen.getContentPane().setBackground(Color.red);
        screen.setLayout(null);

        areaText = new JTextArea("i know you'd never let me down, but will you lift me off the ground? - BoyWithUke");
        areaText.setBounds(0, 520, 1000, 50);
        areaText.setBackground(Color.BLUE);
        areaText.setForeground(Color.white);
        areaText.setEditable(false);
        areaText.setLineWrap(true);
        areaText.setWrapStyleWord(true);
        areaText.setFont(new Font("Book Antiqua", Font.PLAIN, 15));
        screen.add(areaText);
    }
   
    // Background builder
    public void background(int bgNum, String bgFileName) {
        bgPanel[bgNum] = new JPanel();
        bgPanel[bgNum].setBounds(0, 0, 1000, 600);
        bgPanel[bgNum].setLayout(null);
        screen.add(bgPanel[bgNum]);

        bgLabel[bgNum] = new JLabel();
        bgLabel[bgNum].setBounds(0, 0, 1000, 600);

        ImageIcon bgIcon = new ImageIcon("images/" + bgFileName);
        Image bgImage = bgIcon.getImage();
        Image scaledImage = bgImage.getScaledInstance(1000, 600, Image.SCALE_SMOOTH);
        bgLabel[bgNum].setIcon(new ImageIcon(scaledImage));
    }

    // Object builder
    public void object(int bgNum, int objx, int objy, int objWidth, int objHeight, String objFileName, String select1, String select2, String command1, String command2) {
        JPopupMenu Menu = new JPopupMenu();
        JMenuItem menuItem[] = new JMenuItem[2];
        
        menuItem[0] = new JMenuItem(select1);
        menuItem[0].addActionListener(gm.action);
        menuItem[0].setActionCommand(command1);
        Menu.add(menuItem[0]);

        menuItem[1] = new JMenuItem(select2);
        menuItem[1].addActionListener(gm.action);
        menuItem[1].setActionCommand(command2);
        Menu.add(menuItem[1]);

        JLabel objLabel = new JLabel();
        objLabel.setBounds(objx, objy, objWidth, objHeight);

        ImageIcon objIcon = new ImageIcon("images/" + objFileName);
        objLabel.setIcon(objIcon);

        objLabel.addMouseListener(new MouseListener() {
            public void mouseClicked(MouseEvent e) {}
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    Menu.show(objLabel, e.getX(), e.getY());
                }
            }
            public void mouseReleased(MouseEvent e) {}
            public void mouseEntered(MouseEvent e) {}
            public void mouseExited(MouseEvent e) {}
        });
        
        bgPanel[bgNum].add(bgLabel[bgNum]);
        bgPanel[bgNum].add(objLabel);
        bgPanel[bgNum].add(objLabel);
        bgPanel[bgNum].setComponentZOrder(objLabel, 0);
    }

    // Game Main Menu panel
    public void gameMainMenu(int bgNum){
        JPanel gameMenu = new JPanel();
        gameMenu.setBounds(930, 11, 50, 51);
        ImageIcon gameMenuIcon = new ImageIcon("images/" + "screenMenu2.png");
        JButton menuButton = new JButton(new ImageIcon(gameMenuIcon));

        bgPanel[bgNum].add(gameMenu);
    }

    // Calls the background and objects
    public void scene() {
        background(0, "cavern1.png");
        gameMainMenu(0); 
        object(0, 200, 300, 200, 200, "entrance2.png", "Light", "Enter", "useSpell", "enterCave");
        object(0, 400, 320, 200, 200, "entrance2.png", "Light", "Enter", "useSpell", "enterCave");
        object(0, 600, 315, 200, 200, "entrance2.png", "Light", "Enter", "useSpell", "enterCave");
    }
}

