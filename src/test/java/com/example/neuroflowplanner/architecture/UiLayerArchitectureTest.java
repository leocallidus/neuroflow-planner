package com.example.neuroflowplanner.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
    packages = "com.example.neuroflowplanner",
    importOptions = {ImportOption.DoNotIncludeTests.class}
)
class UiLayerArchitectureTest {

    @ArchTest
    static final ArchRule decomposedUiPackagesShouldNotDependOnDbOrInfra = noClasses()
        .that().resideInAnyPackage("..ui.mainview..", "..ui.smartnotes..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("..db..", "java.net.http..", "com.itextpdf..", "org.apache.poi..");

    @ArchTest
    static final ArchRule mainViewEntryAdapterShouldNotDependOnDbOrInfra = noClasses()
        .that().haveSimpleName("MainView")
        .should().dependOnClassesThat()
        .resideInAnyPackage("..db..", "java.net.http..", "com.itextpdf..", "org.apache.poi..");

    @ArchTest
    static final ArchRule smartNotesEntryAdapterShouldNotDependOnDbOrInfra = noClasses()
        .that().haveSimpleName("SmartNotesDialog")
        .should().dependOnClassesThat()
        .resideInAnyPackage("..db..", "java.net.http..", "com.itextpdf..", "org.apache.poi..");

    @ArchTest
    static final ArchRule viewClassesShouldNotDependOnDatabaseManager = noClasses()
        .that().resideInAnyPackage("..ui.mainview..", "..ui.smartnotes..")
        .and().haveSimpleNameEndingWith("View")
        .should().dependOnClassesThat()
        .haveFullyQualifiedName("com.example.neuroflowplanner.db.DatabaseManager");

    @ArchTest
    static final ArchRule presentersShouldNotBuildLayouts = noClasses()
        .that().resideInAnyPackage("..ui.mainview..", "..ui.smartnotes..")
        .and().haveSimpleNameEndingWith("Presenter")
        .should().dependOnClassesThat()
        .resideInAnyPackage("javafx.scene.layout..", "javafx.scene.control..");

    @ArchTest
    static final ArchRule uiLayerShouldNotDependOnJavaHttpClient = noClasses()
        .that().resideInAnyPackage("..ui..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("java.net.http..");
}
