package com.redisplus.core.desktop;

import com.redisplus.MainApplication;
import com.redisplus.base.controller.ConnectController;
import com.redisplus.base.controller.DataSinglesController;
import com.redisplus.base.controller.JsCallJava;
import com.redisplus.tool.ConfIO;
import com.sun.javafx.webkit.WebConsoleListener;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import netscape.javascript.JSObject;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;

import static com.redisplus.tool.ItemUtil.*;


public class Desktop extends Application {

    private double x = 0.00;
    private double y = 0.00;

    private double xOffset = 0;
    private double yOffset = 0;

    private double width = 0.00;
    private double height = 0.00;

    private double resizeWidth = 5.00;
    private double minWidth = 1000.00;
    private double minHeight = 600.00;

    //是否处于右边界调整窗口状态
    private boolean isRight;
    //是否处于下边界调整窗口状态
    private boolean isBottom;
    //是否处于右下角调整窗口状态
    private boolean isBottomRight;
    //是否处于最大化调整窗口状态
    private boolean isMax = false;

    private static WebView webView;
    private static WebEngine webEngine;

    //上下文对象
    public static ConfigurableApplicationContext context = null;

    //注入的JS对象
    private static JsCallJava jsCallJava = null;

    @Override
    public void start(Stage winStage) {

        //设置窗口信息
        winStage.centerOnScreen();
        winStage.setTitle(DESKTOP_APP_NAME);
        winStage.setAlwaysOnTop(false);
        winStage.initStyle(StageStyle.TRANSPARENT);

        //启动扫描服务
        context = SpringApplication.run(MainApplication.class);
        if (null != context) {
            initWebObject();
        } else {
            return;
        }

        //加载数据窗口
        BorderPane mainView = getMainView(winStage);
        winStage.setScene(new Scene(mainView, minWidth, minHeight));
        winStage.show();

        //监听窗口事件
        doWinStage(winStage);
        doWinRaise(winStage);
        doWinState(winStage, mainView);
    }


    /**
     * 窗口主体
     */
    private BorderPane getMainView(Stage winStage) {
        BorderPane mainView = new BorderPane();
        mainView.setId("main-view");
        mainView.getStylesheets().add(DESKTOP_STYLE);
        mainView.setTop(getTopsView(winStage));
        mainView.setCenter(getBodyView());
        return mainView;
    }


    /**
     * 顶部标题栏
     */
    private GridPane getTopsView(Stage winStage) {

        GridPane topsView = new GridPane();
        topsView.setId("tops-view");
        topsView.setHgap(10);

        Label topImage = new Label();
        Label topTitle = new Label();
        Label topAbate = new Label();
        Label topRaise = new Label();
        Label topClose = new Label();

        topTitle.setText(DESKTOP_APP_NAME);
        topImage.setId("tops-view-image");
        topTitle.setId("tops-view-title");
        topAbate.setId("tops-view-abate");
        topRaise.setId("tops-view-raise");
        topClose.setId("tops-view-close");

        topImage.setLayoutX(8);
        topImage.setPrefSize(20, 16);
        topAbate.setPrefSize(27, 23);
        topRaise.setPrefSize(27, 23);
        topClose.setPrefSize(27, 23);

        topsView.add(topImage, 0, 0);
        topsView.add(topTitle, 1, 0);
        topsView.add(topAbate, 3, 0);
        topsView.add(topRaise, 4, 0);
        topsView.add(topClose, 5, 0);

        topsView.setPadding(new Insets(5));
        topsView.setAlignment(Pos.CENTER_LEFT);
        GridPane.setHgrow(topTitle, Priority.ALWAYS);
        String themeColor = "#D6D6D7";
        Color backgroundColor = Color.web(themeColor, 1.0);
        BackgroundFill backgroundFill = new BackgroundFill(backgroundColor, null, null);
        topsView.setBackground(new Background(backgroundFill));

        //事件监听
        topAbate.setOnMouseClicked(event -> doWinAbate(winStage));
        topRaise.setOnMouseClicked(event -> doWinRaise(winStage));
        topClose.setOnMouseClicked(event -> doWinClose(winStage));
        return topsView;
    }


    /**
     * 内容窗体
     */
    private WebView getBodyView() {

        webView = new WebView();
        webView.setCache(false);
        webEngine = webView.getEngine();
        webView.setContextMenuEnabled(true);
        webEngine.setJavaScriptEnabled(true);

        //设置加载的主页
        String rootPagePath;
        rootPagePath = PAGE_CONNECT_DEFAULT;
        webEngine.load(Desktop.class.getResource(rootPagePath).toExternalForm());

        String dataPath = new ConfIO().getDataPath();
        webEngine.setUserDataDirectory(new File(dataPath));

        //监听事件
        Worker<Void> woker = webEngine.getLoadWorker();
        woker.stateProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == Worker.State.SUCCEEDED) {
                JSObject jsObject = (JSObject) webEngine.executeScript("window");
                jsObject.setMember("jsCallJava", jsCallJava);
            }
        });

        //页面异常事件
        woker.exceptionProperty().addListener((ObservableValue<? extends Throwable> ov, Throwable t0, Throwable t1) ->
            System.err.println("Received Exception: " + t1.getMessage())
        );

        //控制台监听事件
        WebConsoleListener.setDefaultListener((WebView webView, String message, int lineNumber, String sourceId) ->
            System.out.println("Console: [" + sourceId + ":" + lineNumber + "] " + message)
        );

        return webView;
    }

    /**
     * 监听窗口属性事件
     */
    private void doWinStage(Stage winStage) {
        winStage.xProperty().addListener((ObservableValue<? extends Number> observable, Number oldValue, Number newValue) -> {
            if (newValue != null && !isMax) {
                x = newValue.doubleValue();
            }
        });
        winStage.yProperty().addListener((ObservableValue<? extends Number> observable, Number oldValue, Number newValue) -> {
            if (newValue != null && !isMax) {
                y = newValue.doubleValue();
            }
        });
        winStage.widthProperty().addListener((ObservableValue<? extends Number> observable, Number oldValue, Number newValue) -> {
            if (newValue != null && !isMax) {
                width = newValue.doubleValue();
            }
        });
        winStage.heightProperty().addListener((ObservableValue<? extends Number> observable, Number oldValue, Number newValue) -> {
            if (newValue != null && !isMax) {
                height = newValue.doubleValue();
            }
        });
    }


    /**
     * 监听窗口操作事件
     */
    private void doWinState(Stage winStage, BorderPane mainView) {
        //监听窗口移动后事件
        mainView.setOnMouseMoved((MouseEvent event) -> {
            event.consume();
            double tx = event.getSceneX();//记录x数据
            double ty = event.getSceneY();//记录y数据
            double tw = winStage.getWidth();//记录width数据
            double th = winStage.getHeight();//记录height数据
            //光标初始为默认类型, 若未进入调整窗口状态则保持默认类型
            Cursor cursorType = Cursor.DEFAULT;
            //将所有调整窗口状态重置
            isRight = isBottomRight = isBottom = false;
            if (ty >= th - resizeWidth) {
                if (tx <= resizeWidth) {
                    //左下角调整窗口状态
                } else if (tx >= tw - resizeWidth) {
                    //右下角调整窗口状态
                    isBottomRight = true;
                    cursorType = Cursor.SE_RESIZE;
                } else {
                    //下边界调整窗口状态
                    isBottom = true;
                    cursorType = Cursor.S_RESIZE;
                }
            } else if (tx >= tw - resizeWidth) {
                // 右边界调整窗口状态
                isRight = true;
                cursorType = Cursor.E_RESIZE;
            }
            // 最后改变鼠标光标
            mainView.setCursor(cursorType);
        });

        //监听窗口拖拽后事件
        mainView.setOnMouseDragged((MouseEvent event) -> {
            event.consume();
            if (yOffset != 0) {
                winStage.setX(event.getScreenX() - xOffset);
                if (event.getScreenY() - yOffset < 0) {
                    winStage.setY(0);
                } else {
                    winStage.setY(event.getScreenY() - yOffset);
                }
            }
            double tx = event.getSceneX();
            double ty = event.getSceneY();
            //保存窗口改变后的x、y坐标和宽度、高度, 用于预判是否会小于最小宽度、最小高度
            double nextX = winStage.getX();
            double nextY = winStage.getY();
            double nextWidth = winStage.getWidth();
            double nextHeight = winStage.getHeight();
            if (isRight || isBottomRight) {
                // 所有右边调整窗口状态
                nextWidth = tx;
            }
            if (isBottomRight || isBottom) {
                // 所有下边调整窗口状态
                nextHeight = ty;
            }
            if (nextWidth <= minWidth) {
                // 如果窗口改变后的宽度小于最小宽度, 则宽度调整到最小宽度
                nextWidth = minWidth;
            }
            if (nextHeight <= minHeight) {
                // 如果窗口改变后的高度小于最小高度, 则高度调整到最小高度
                nextHeight = minHeight;
            }
            // 最后统一改变窗口的x、y坐标和宽度、高度, 可以防止刷新频繁出现的屏闪情况
            winStage.setX(nextX);
            winStage.setY(nextY);
            winStage.setWidth(nextWidth);
            winStage.setHeight(nextHeight);

        });

        //鼠标点击获取横纵坐标
        mainView.setOnMousePressed(event -> {
            event.consume();
            xOffset = event.getSceneX();
            if (event.getSceneY() > 46) {
                yOffset = 0;
            } else {
                yOffset = event.getSceneY();
            }
        });

    }

    /**
     * 监听窗口最小事件
     */
    private void doWinAbate(Stage winStage) {
        winStage.setIconified(true);
    }


    /**
     * 监听窗口最大事件
     */
    private void doWinRaise(Stage winStage) {
        Rectangle2D rectangle2d = Screen.getPrimary().getVisualBounds();
        isMax = !isMax;
        if (isMax) {
            // 最大化
            winStage.setX(rectangle2d.getMinX());
            winStage.setY(rectangle2d.getMinY());
            winStage.setWidth(rectangle2d.getWidth());
            winStage.setHeight(rectangle2d.getHeight());
            webView.setPrefSize(rectangle2d.getWidth(), rectangle2d.getHeight());
        } else {
            if (x == 0 && y == 0 && width == 0 && height == 0) {
                winStage.setWidth(minWidth);
                winStage.setHeight(minHeight);
                winStage.centerOnScreen();
                webView.setPrefSize(minWidth, minHeight);
            } else {
                // 缩放回原来的大小
                winStage.setX(x);
                winStage.setY(y);
                winStage.setWidth(width);
                winStage.setHeight(height);
                webView.setPrefSize(width, height);
            }
        }
    }

    /**
     * 监听窗口关闭事件
     */
    private void doWinClose(Stage winStage) {
        winStage.close();
        Platform.exit();
        System.exit(0);
    }

    public static void setWebViewPage(String url) {
        String utl = Desktop.class.getResource(url).toExternalForm();
        webEngine.load(utl);
    }

    private void initWebObject() {
        ConnectController conn = context.getBean(ConnectController.class);
        DataSinglesController data = context.getBean(DataSinglesController.class);
        jsCallJava = context.getBean(JsCallJava.class);
        jsCallJava.init(conn, data);
    }
}
