package com.larryhsiao.auxo.controller;

import com.larryhsiao.auxo.dialogs.ExceptionAlert;
import com.larryhsiao.auxo.tagging.*;
import com.larryhsiao.auxo.utils.Execute;
import com.larryhsiao.auxo.views.FileListCell;
import com.larryhsiao.auxo.workspace.FsFiles;
import com.silverhetch.clotho.Source;
import com.silverhetch.clotho.database.SingleConn;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import static javafx.scene.input.KeyCode.ENTER;
import static javafx.scene.input.MouseButton.PRIMARY;

/**
 * Controller of page that shows file list in Axuo.
 */
public class FileList implements Initializable {
    private final Source<Connection> db = new SingleConn(new TagDbConn());
    private final ObservableList<File> data = FXCollections.observableArrayList();
    @FXML private TextField searchInput;
    @FXML private ListView<File> fileList;
    @FXML private AnchorPane info;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        searchInput.textProperty().addListener((observable, oldValue, newValue) -> {
            String keyword = searchInput.textProperty().getValue();
            Map<String, AFile> dbSearchRes = new QueriedAFiles(new FilesByKeyword(db, keyword)).value();
            data.clear();
            data.addAll(
                new FsFiles().value().entrySet().stream()
                    .filter(entry -> entry.getKey().contains(keyword) ||
                        dbSearchRes.containsKey(entry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)).values()
            );
        });
        data.addAll(new FsFiles().value().values());
        fileList.setCellFactory(param -> new FileListCell());
        fileList.setOnContextMenuRequested(event -> {
            final ContextMenu menu = new ContextMenu();
            menu.show(fileList, event.getScreenX(), event.getScreenY());
        });
        fileList.setItems(data);
        fileList.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<File>() {
            @Override
            public void changed(ObservableValue<? extends File> observable, File oldValue, File newValue) {
                if (newValue == null) {
                    return;
                }
                loadInfo(newValue, resources);
            }
        });
        fileList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && event.getButton() == PRIMARY) {
                openSelectedFile(resources);
            }
        });
        fileList.setOnKeyPressed(event -> {
            if (event.getCode() == ENTER) {
                openSelectedFile(resources);
            }
        });
    }

    private void openSelectedFile(ResourceBundle res) {
        final File selectedFile = fileList.getSelectionModel().getSelectedItem();
        if (selectedFile == null) {
            return;
        }
        if (selectedFile.isDirectory()) {
            fileBrowse(selectedFile, res);
        } else {
            new Thread(() -> {
                try {
                    new Execute(selectedFile).fire();
                } catch (Exception e) {
                    Platform.runLater(() -> new ExceptionAlert(e, res).fire());
                }
            }).start();
        }
    }

    private void fileBrowse(File selected, ResourceBundle res) {
        try {
            final Stage currentStage = ((Stage) fileList.getScene().getWindow());
            final FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/larryhsiao/auxo/file_browse.fxml"), res);
            loader.setController(new FileBrowse(selected));
            final Stage newStage = new Stage();
            newStage.setTitle(selected.getName());
            newStage.setScene(new Scene(loader.load()));
            newStage.setX(currentStage.getX() + 100);
            newStage.setY(currentStage.getY() + 100);
            newStage.addEventHandler(KeyEvent.KEY_RELEASED, new EventHandler<KeyEvent>() {
                @Override
                public void handle(KeyEvent event) {
                    if (event.getCode() == KeyCode.ESCAPE) {
                        newStage.close();
                    }
                }
            });
            newStage.show();
        } catch (Exception e) {
            new ExceptionAlert(e, res).fire();
        }
    }

    private void loadInfo(File selected, ResourceBundle res) {
        try {
            final FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/larryhsiao/auxo/file.fxml"), res);
            loader.setController(new FileInfo(
                    new FileByName(
                        db,
                        selected.getName()
                    ).value().id()
                )
            );
            info.getChildren().clear();
            info.getChildren().add(loader.load());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
