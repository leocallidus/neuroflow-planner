module com.example.neuroflowplanner {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires org.kordamp.bootstrapfx.core;
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.materialdesign2;
    requires java.sql;
    requires java.net.http;
    requires java.prefs;
    requires org.apache.poi.ooxml;
    requires kernel;
    requires layout;
    requires io;

    opens com.example.neuroflowplanner to javafx.fxml;
    exports com.example.neuroflowplanner;
}
