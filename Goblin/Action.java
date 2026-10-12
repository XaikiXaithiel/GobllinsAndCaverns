package Goblin;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class Action implements ActionListener {

    Main gm;

    public Action(Main gm) {
        
        this.gm = gm;

    }

    @Override
    public void actionPerformed(ActionEvent e) {
        
        String yourChoice = e.getActionCommand();

        switch (yourChoice) {
            case "useSpell":
                gm.ui.areaText.setText( "You used a Light Spell!");
                break;
        
            case "enterCave":
                gm.ui.areaText.setText( "You ran inside!");
                break;
        }

    } 

}
