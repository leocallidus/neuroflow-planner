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
    requires jdk.httpserver;
    requires java.prefs;
    requires java.desktop;
    requires org.apache.poi.ooxml;
    requires kernel;
    requires layout;
    requires io;
    requires flyway.core;
    requires org.slf4j;
    requires com.fasterxml.jackson.databind;
    requires com.networknt.schema;

    opens com.example.neuroflowplanner to javafx.fxml;
    opens com.example.neuroflowplanner.ai.dto to com.fasterxml.jackson.databind;
    opens com.example.neuroflowplanner.ai.dto.ui to com.fasterxml.jackson.databind;
    opens com.example.neuroflowplanner.sync to com.fasterxml.jackson.databind;
    exports com.example.neuroflowplanner;
    exports com.example.neuroflowplanner.ai;
    exports com.example.neuroflowplanner.sync;
}
