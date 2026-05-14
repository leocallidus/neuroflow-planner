package com.example.neuroflowplanner.architecture;

import com.example.neuroflowplanner.service.ChatBotService;
import com.example.neuroflowplanner.service.context.ChatContextManager;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
    packages = "com.example.neuroflowplanner",
    importOptions = {ImportOption.DoNotIncludeTests.class}
)
class ChatAssistantArchitectureGuardTest {

    @ArchTest
    static final ArchRule chatUiShouldNotDependOnLowLevelHttp = noClasses()
        .that().resideInAnyPackage("..ui..")
        .and().haveSimpleNameContaining("Chat")
        .should().dependOnClassesThat()
        .resideInAnyPackage("java.net.http..");

    @ArchTest
    static final ArchRule onlyChatBotServiceMayDependOnChatContextManager = noClasses()
        .that().doNotHaveFullyQualifiedName(ChatBotService.class.getName())
        .should().dependOnClassesThat()
        .haveFullyQualifiedName(ChatContextManager.class.getName());
}
