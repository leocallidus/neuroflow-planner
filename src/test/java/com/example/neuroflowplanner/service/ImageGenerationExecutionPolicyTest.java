package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.service.imagejob.ImageJobSnapshot;
import com.example.neuroflowplanner.testinfra.IsolatedTestDataFixture;
import com.example.neuroflowplanner.util.ConfigManager;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageGenerationExecutionPolicyTest extends IsolatedTestDataFixture {

    private final ImageGenerationService service = ImageGenerationService.getInstance();

    @BeforeEach
    void setUpConfig() {
        ConfigManager.setProperty("ai.image.fallback.enabled", "true");
        ConfigManager.setProperty(
            "ai.image.fallback.models",
            String.join(",",
                "gemini-3-pro-image-preview",
                "nano-banana",
                "seedream-v4.5")
        );
        ConfigManager.setProperty("ai.image.request.totalBudgetMs", "10000");
        ConfigManager.setProperty("ai.image.retry.baseDelayMs", "100");
        ConfigManager.setProperty("ai.image.retry.maxDelayMs", "100");
        ConfigManager.setProperty("ai.image.poll.initialDelayMs", "250");
        ConfigManager.setProperty("ai.image.poll.maxDelayMs", "250");
    }

    @Test
    void executionPlanAddsFallbackModelsWithoutDuplicates() throws Exception {
        Object config = invoke(service, "loadExecutionConfig");
        ImageGenerationService.ImageGenerationOptions options =
            new ImageGenerationService.ImageGenerationOptions("nano-banana", "", "16:9", "", "", "", "", "");

        Object plan = invoke(
            service,
            "buildExecutionPlan",
            new Class<?>[]{
                String.class,
                ImageGenerationService.ImageGenerationOptions.class,
                String.class,
                String.class,
                String.class,
                config.getClass()
            },
            "A scenic test prompt",
            options,
            "nano-banana",
            "test-key",
            "http://example.invalid",
            config
        );

        @SuppressWarnings("unchecked")
        List<String> candidateModels = (List<String>) invoke(plan, "candidateModels");
        assertEquals(
            List.of(
                "google/gemini-2.5-flash-image",
                "google/gemini-3-pro-image-preview",
                "bytedance/seedream-4.5"),
            candidateModels);
    }

    @Test
    void resolveModelExecutionAdaptsPrimaryFieldBetweenSizeAndAspectRatio() throws Exception {
        ImageGenerationService.ImageGenerationOptions options =
            new ImageGenerationService.ImageGenerationOptions("nano-banana", "", "16:9", "2K", "", "", "", "");

        Object execution = invoke(
            service,
            "resolveModelExecution",
            new Class<?>[]{ImageGenerationService.ImageGenerationOptions.class, String.class},
            options,
            "gemini-3-pro-image-preview"
        );
        Object validatedOptions = invoke(execution, "options");

        assertEquals("google/gemini-3-pro-image-preview", invoke(validatedOptions, "model"));
        assertEquals("16:9", invoke(validatedOptions, "aspectRatio"));
        assertEquals("2K", invoke(validatedOptions, "resolution"));
        assertEquals("", invoke(validatedOptions, "size"));
    }

    @Test
    void exhaustedBudgetClampsRetryDelayToZero() throws Exception {
        Object config = invoke(service, "loadExecutionConfig");
        ImageJobSnapshot snapshot = new ImageJobSnapshot();
        snapshot.setJobId("job-budget");
        snapshot.setRequestedModel("google/gemini-2.5-flash-image");
        snapshot.setCreatedAt(System.currentTimeMillis() - Duration.ofSeconds(15).toMillis());
        snapshot.setAttempt(1);

        Class<?> runtimeClass = Class.forName("com.example.neuroflowplanner.service.ImageGenerationService$ImageRequestRuntime");
        Constructor<?> ctor = runtimeClass.getDeclaredConstructor(ImageJobSnapshot.class);
        ctor.setAccessible(true);
        Object runtime = ctor.newInstance(snapshot);

        long delayMs = (long) invoke(
            service,
            "nextRetryDelayMs",
            new Class<?>[]{config.getClass(), int.class, runtimeClass},
            config,
            2,
            runtime
        );

        assertEquals(0L, delayMs);
        assertTrue((boolean) invoke(config, "fallbackEnabled"));
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object invoke(Object target, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
}
