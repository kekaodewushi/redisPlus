package com.redisplus;

import com.redisplus.core.desktop.Desktop;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MainApplication extends Desktop {

    public static void main(String[] args) {
        //System.setProperty("javafx.preloader", "com.redisplus.core.desktop.AppGuide");
        launch(args);
    }
}
