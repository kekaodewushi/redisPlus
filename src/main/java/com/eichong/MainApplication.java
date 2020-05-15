package com.eichong;

import com.eichong.core.desktop.Desktop;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MainApplication extends Desktop {

    public static void main(String[] args) {
        //System.setProperty("javafx.preloader", "com.eichong.core.desktop.AppGuide");
        launch(args);
    }
}
