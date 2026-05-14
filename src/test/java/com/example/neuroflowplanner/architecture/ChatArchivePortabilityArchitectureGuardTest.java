package com.example.neuroflowplanner.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
    packages = "com.example.neuroflowplanner",
    importOptions = {ImportOption.DoNotIncludeTests.class}
)
class ChatArchivePortabilityArchitectureGuardTest {

    @ArchTest
    static final ArchRule chatBotDialogMustNotDependOnFileWriterForArchiveWriting = noClasses()
        .that().haveSimpleName("ChatBotDialog")
        .should().dependOnClassesThat()
        .haveFullyQualifiedName(FileWriter.class.getName());

    @ArchTest
    static final ArchRule chatBotDialogMustNotDependOnFileOutputStreamForArchiveWriting = noClasses()
        .that().haveSimpleName("ChatBotDialog")
        .should().dependOnClassesThat()
        .haveFullyQualifiedName(FileOutputStream.class.getName());

    @ArchTest
    static final ArchRule chatBotDialogMustNotDependOnOutputStreamWriterForArchiveWriting = noClasses()
        .that().haveSimpleName("ChatBotDialog")
        .should().dependOnClassesThat()
        .haveFullyQualifiedName(OutputStreamWriter.class.getName());

    @ArchTest
    static final ArchRule chatBotDialogMustNotDependOnPrintWriterForArchiveWriting = noClasses()
        .that().haveSimpleName("ChatBotDialog")
        .should().dependOnClassesThat()
        .haveFullyQualifiedName(PrintWriter.class.getName());

    @ArchTest
    static final ArchRule settingsDialogMustNotDependOnChatArchiveJsonCodec = noClasses()
        .that().haveSimpleName("SettingsDialog")
        .should().dependOnClassesThat()
        .haveFullyQualifiedName("com.example.neuroflowplanner.service.chatio.ChatArchiveJsonCodec");

    @ArchTest
    static final ArchRule settingsDialogMustNotDependOnJacksonObjectMapper = noClasses()
        .that().haveSimpleName("SettingsDialog")
        .should().dependOnClassesThat()
        .haveFullyQualifiedName(ObjectMapper.class.getName());

    @ArchTest
    static final ArchRule settingsDialogMustNotDependOnJacksonJsonNode = noClasses()
        .that().haveSimpleName("SettingsDialog")
        .should().dependOnClassesThat()
        .haveFullyQualifiedName(JsonNode.class.getName());
}
