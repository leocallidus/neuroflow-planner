package com.example.neuroflowplanner.architecture;

import com.example.neuroflowplanner.ai.ExternalOpenAiClient;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
    packages = "com.example.neuroflowplanner",
    importOptions = {ImportOption.DoNotIncludeTests.class}
)
class ImageGenerationUiArchitectureGuardTest {

    @ArchTest
    static final ArchRule imageUiMustNotDependOnLowLevelHttp = noClasses()
        .that().haveSimpleName("ChatBotDialog")
        .should().dependOnClassesThat()
        .resideInAnyPackage("java.net.http..");

    @ArchTest
    static final ArchRule imageUiMustNotDependOnProviderClientImplementation = noClasses()
        .that().haveSimpleName("ChatBotDialog")
        .should().dependOnClassesThat()
        .haveFullyQualifiedName(ExternalOpenAiClient.class.getName());
}
