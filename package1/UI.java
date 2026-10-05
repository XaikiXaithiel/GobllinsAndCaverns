package package1;

import java.awt.Color;

import javax.swing.JFrame;

public class UI {

    GameMain gm;
    JFrame window;

    public UI(GameMain gm) {
        this.gm = gm;
        createMainField();
        window.setVisible(true);
    }
    public void createMainField() {
        window = new JFrame();
        window.setSize(1000,600);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.getContentPane().setBackground(Color.GRAY);
        window.setLayout(null);

    }
}
