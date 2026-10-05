package package1;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class Actions implements ActionListener {

    GameMain gm;

    public Actions(GameMain gm){
        this.gm = gm;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String yourChoice = e.getActionCommand();

        switch (yourChoice) {
            case "useSpell":
                gm.ui.areaText.setText("light spell used");
                break;
            case "runInside":
                gm.ui.areaText.setText("you ran inside");
                break;
        }
    }
}
