package package1;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class UI {

    GameMain gm;
    JFrame window;
    public JTextArea areaText;
    public JPanel bgPanel[] = new JPanel[10];
    public JLabel bgLabel[] = new JLabel[10];
    public UI(GameMain gm) {
        this.gm = gm;
        createMainField();
        scene();
        window.setVisible(true);
    }
    public void createMainField() {
        window = new JFrame();
        window.setSize(1000,600);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.getContentPane().setBackground(Color.GRAY);
        window.setLayout(null);

        areaText = new JTextArea("alien beak w ostrich feet");
        areaText.setBounds(50,410,700,120);
        areaText.setBackground(Color.blue);
        areaText.setForeground(Color.white);
        areaText.setEditable(true);
        areaText.setLineWrap(true);
        areaText.setWrapStyleWord(true);
        areaText.setFont(new Font("Book Antiqua",Font.PLAIN,20));
        window.add(areaText);
    }
    public void createBg(int bgNum, String bgFileName) {
        bgPanel[bgNum] = new JPanel();
        bgPanel[bgNum].setBounds(50,50,875,350);
        bgPanel[bgNum].setBackground(Color.red);
        bgPanel[bgNum].setLayout(null);
        window.add(bgPanel[1]);

        //this will be used for image display(res folder), talk to frontend for img -xaiki
        bgLabel[bgNum] = new JLabel();
        bgLabel[bgNum].setBounds(0,0,875,350);

        ImageIcon bgIcon = new ImageIcon(getClass().getClassLoader().getResource(bgFileName));
        bgLabel[bgNum].setIcon(bgIcon);
    }
    public void createObject(int bgNum, int objx, int objy, int objWidth, int objHeight, String objFileName,
        String select1, String select2, String select1Command, String Select2Command) {

        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem menuItem[] = new JMenuItem[3];
        
        menuItem[1] = new JMenuItem(select1);
        menuItem[1].addActionListener(gm.action);
        menuItem[1].setActionCommand(select1Command);
        popupMenu.add(menuItem[1]);

        menuItem[2] = new JMenuItem(select2);
        menuItem[2].addActionListener(gm.action);
        menuItem[2].setActionCommand(Select2Command);
        popupMenu.add(menuItem[2]);

        JLabel objectJLabel = new JLabel();
        objectJLabel.setBounds(objx,objy,objWidth,objHeight);
        ImageIcon objectIcon = new ImageIcon(getClass().getClassLoader().getResource(objFileName));
        objectJLabel.setIcon(objectIcon);

        //the most important piece of code
        objectJLabel.addMouseListener(new MouseListener() {
            public void mouseClicked(MouseEvent e){}
            public void mousePressed(MouseEvent e){
                if (SwingUtilities.isRightMouseButton(e)){
                    popupMenu.show(objectJLabel, e.getX(), e.getY());
                }
            }
            public void mouseReleased(MouseEvent e){}
            public void mouseEntered(MouseEvent e){}
            public void mouseExited(MouseEvent e){}
        });
        //my brain is fried :< -xaiki

        bgPanel[bgNum].add(objectJLabel);
        bgPanel[bgNum].add(bgLabel[bgNum]);
    }
    public void scene(){
        //scene 1 make sure to replace null with file name when there are pictures
        createBg(1, null);
        createObject(1,400,150,200,200, null, "Light", "Run", "useSpell", "runInside");
        // feel free to spam call this method when you want multiple objects^

    }
}
