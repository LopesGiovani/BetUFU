package br.com.betufu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Regras de arquitetura para garantia estrutural de privacidade (PRIV-01).
 *
 * <p>Impede que dados sensíveis (matrícula crua e HMAC/digest) alcancem:
 * <ol>
 *   <li>Campos, construtores ou records fora do módulo de verificação (nome e tipo);</li>
 *   <li>Argumentos ou escopos de chamadas a loggers (política conservadora: sem logger no núcleo de verificação e sem logger em código com tipos/vocabulário sensíveis);</li>
 *   <li>Componentes de records que participam do retorno de controladores HTTP (detecção estrutural completa via reflexão padrão Java, incluindo wrappers genéricos e records aninhados);</li>
 *   <li>Rotas, URI templates, @PathVariable ou @RequestParam em controladores HTTP.</li>
 * </ol>
 *
 * <p>Cada regra é acompanhada de prova de discriminação por classe sintética violadora
 * e prova explícita de não vacuidade contra a árvore de produção.
 */
@AnalyzeClasses(
        packages = "br.com.betufu",
        importOptions = ImportOption.DoNotIncludeTests.class)
class PrivacyArchitectureTest {

    // ── Regra 1: Vocabulário e tipos de matrícula crua fora do módulo de verificação ─

    @ArchTest
    public static final ArchRule NO_RAW_MATRICULA_OUTSIDE_VERIFICATION = ArchRuleDefinition.classes()
            .that().resideOutsideOfPackage("br.com.betufu.verification.internal..")
            .and().resideOutsideOfPackage("br.com.betufu.verification..")
            .should(new ArchCondition<JavaClass>(
                    "not declare fields, constructor parameters, or record components with raw matricula vocabulary or types") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    for (JavaField field : javaClass.getAllFields()) {
                        if (isEnumConstant(field, javaClass)) {
                            continue;
                        }
                        if (isRawMatriculaVocabulary(field.getName())) {
                            String message = String.format(
                                    "Class %s declares field '%s' with raw matricula vocabulary",
                                    javaClass.getName(), field.getName());
                            events.add(SimpleConditionEvent.violated(javaClass, message));
                        }
                        if (isRawMatriculaType(field.getRawType())) {
                            String message = String.format(
                                    "Class %s declares field '%s' of sensitive type '%s'",
                                    javaClass.getName(), field.getName(), field.getRawType().getName());
                            events.add(SimpleConditionEvent.violated(javaClass, message));
                        }
                    }

                    for (JavaConstructor constructor : javaClass.getConstructors()) {
                        for (JavaParameter parameter : constructor.getParameters()) {
                            String paramName = getParameterName(parameter);
                            if (isRawMatriculaVocabulary(paramName)) {
                                String message = String.format(
                                        "Class %s declares constructor parameter '%s' with raw matricula vocabulary",
                                        javaClass.getName(), paramName);
                                events.add(SimpleConditionEvent.violated(javaClass, message));
                            }
                            if (isRawMatriculaType(parameter.getRawType())) {
                                String message = String.format(
                                        "Class %s declares constructor parameter '%s' of sensitive type '%s'",
                                        javaClass.getName(), paramName, parameter.getRawType().getName());
                                events.add(SimpleConditionEvent.violated(javaClass, message));
                            }
                        }
                    }

                    try {
                        Class<?> reflected = javaClass.reflect();
                        if (reflected.isRecord()) {
                            for (RecordComponent rc : reflected.getRecordComponents()) {
                                if (isRawMatriculaVocabulary(rc.getName())) {
                                    String message = String.format(
                                            "Record %s declares record component '%s' with raw matricula vocabulary",
                                            javaClass.getName(), rc.getName());
                                    events.add(SimpleConditionEvent.violated(javaClass, message));
                                }
                                if (isRawMatriculaTypeName(rc.getType().getName())) {
                                    String message = String.format(
                                            "Record %s declares record component '%s' of sensitive type '%s'",
                                            javaClass.getName(), rc.getName(), rc.getType().getName());
                                    events.add(SimpleConditionEvent.violated(javaClass, message));
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            });

    // ── Regra 2: Matrícula ou digest em chamadas a logger ─────────────────────

    @ArchTest
    public static final ArchRule NO_MATRICULA_OR_DIGEST_IN_LOGGER = ArchRuleDefinition.classes()
            .should(new ArchCondition<JavaClass>("never call a logger in verification core or in scope of matricula or digest data") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
                        if (isLoggerType(call.getTargetOwner())) {
                            // Política A: Nenhuma chamada a logger no núcleo de verificação
                            if (javaClass.getPackageName().startsWith("br.com.betufu.verification")) {
                                String message = String.format(
                                        "Verification class %s calls logger method %s.%s",
                                        javaClass.getName(),
                                        call.getTargetOwner().getSimpleName(),
                                        call.getTarget().getName());
                                events.add(SimpleConditionEvent.violated(javaClass, message));
                                continue;
                            }

                            // Política B: Nenhuma chamada a logger em código que manipule vocabulário/tipos sensíveis
                            var origin = call.getOrigin();
                            boolean hasSensitiveContext = false;
                            String reason = "";

                            for (JavaParameter param : origin.getParameters()) {
                                if (isSensitiveType(param.getRawType())) {
                                    hasSensitiveContext = true;
                                    reason = "sensitive parameter type " + param.getRawType().getSimpleName();
                                    break;
                                }
                                String paramName = getParameterName(param);
                                if (isSensitiveParamName(paramName)) {
                                    hasSensitiveContext = true;
                                    reason = "sensitive parameter name '" + paramName + "'";
                                    break;
                                }
                            }

                            if (!hasSensitiveContext) {
                                String methodName = origin.getName();
                                if (isSensitiveParamName(methodName)) {
                                    hasSensitiveContext = true;
                                    reason = "sensitive method name '" + methodName + "'";
                                }
                            }

                            if (!hasSensitiveContext) {
                                for (JavaField field : javaClass.getAllFields()) {
                                    if (isEnumConstant(field, javaClass)) {
                                        continue;
                                    }
                                    if (isSensitiveType(field.getRawType()) || isSensitiveParamName(field.getName())) {
                                        hasSensitiveContext = true;
                                        reason = "class field '" + field.getName() + "'";
                                        break;
                                    }
                                }
                            }

                            if (hasSensitiveContext) {
                                String message = String.format(
                                        "Class %s method %s handling sensitive matricula/digest data (%s) calls logger %s.%s",
                                        javaClass.getName(),
                                        origin.getName(),
                                        reason,
                                        call.getTargetOwner().getSimpleName(),
                                        call.getTarget().getName());
                                events.add(SimpleConditionEvent.violated(javaClass, message));
                            }
                        }
                    }
                }
            });

    // ── Regra 3: Record de resposta HTTP expondo matrícula ou digest ──────────

    @ArchTest
    public static final ArchRule NO_MATRICULA_OR_DIGEST_IN_HTTP_RESPONSE = ArchRuleDefinition.classes()
            .that().areAnnotatedWith(RestController.class)
            .or().areAnnotatedWith(Controller.class)
            .or().areAnnotatedWith(RestControllerAdvice.class)
            .or().areAnnotatedWith(ControllerAdvice.class)
            .should(new ArchCondition<JavaClass>("not expose raw matricula or digest in any HTTP response record structure") {
                @Override
                public void check(JavaClass controllerClass, ConditionEvents events) {
                    Set<JavaClass> responseRecords = extractResponseRecords(controllerClass);
                    for (JavaClass recordClass : responseRecords) {
                        checkResponseRecordStructure(recordClass, controllerClass, events);
                    }
                }
            });

    // ── Regra 4: Controlador com matrícula em rotas, templates, PathVariable ou RequestParam ──

    @ArchTest
    public static final ArchRule NO_MATRICULA_IN_CONTROLLER_URL_OR_PARAMS = ArchRuleDefinition.classes()
            .that().areAnnotatedWith(RestController.class)
            .or().areAnnotatedWith(Controller.class)
            .should(new ArchCondition<JavaClass>("not declare route templates, @PathVariable, or @RequestParam with matricula or digest vocabulary or types") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    checkClassLevelMappingPaths(javaClass, events);

                    for (JavaMethod method : javaClass.getMethods()) {
                        checkMappingPaths(method, javaClass, events);

                        for (JavaParameter parameter : method.getParameters()) {
                            if (isPathVariableParameter(parameter)) {
                                String paramName = getPathVariableName(parameter);
                                if (isSensitiveParamOrPath(paramName)) {
                                    String message = String.format(
                                            "Controller %s.%s declares @PathVariable '%s' with sensitive vocabulary",
                                            javaClass.getSimpleName(), method.getName(), paramName);
                                    events.add(SimpleConditionEvent.violated(javaClass, message));
                                }
                                if (isSensitiveType(parameter.getRawType())) {
                                    String message = String.format(
                                            "Controller %s.%s declares @PathVariable '%s' of sensitive type '%s'",
                                            javaClass.getSimpleName(), method.getName(), paramName, parameter.getRawType().getName());
                                    events.add(SimpleConditionEvent.violated(javaClass, message));
                                }
                            }
                            if (isRequestParamParameter(parameter)) {
                                String paramName = getRequestParamName(parameter);
                                if (isSensitiveParamOrPath(paramName)) {
                                    String message = String.format(
                                            "Controller %s.%s declares @RequestParam '%s' with sensitive vocabulary",
                                            javaClass.getSimpleName(), method.getName(), paramName);
                                    events.add(SimpleConditionEvent.violated(javaClass, message));
                                }
                                if (isSensitiveType(parameter.getRawType())) {
                                    String message = String.format(
                                            "Controller %s.%s declares @RequestParam '%s' of sensitive type '%s'",
                                            javaClass.getSimpleName(), method.getName(), paramName, parameter.getRawType().getName());
                                    events.add(SimpleConditionEvent.violated(javaClass, message));
                                }
                            }
                        }
                    }
                }
            });

    // ── Provas de Discriminação ───────────────────────────────────────────────

    @Test
    @DisplayName("Prova de discriminação: NO_RAW_MATRICULA_OUTSIDE_VERIFICATION falha quando classe externa declara campo/parâmetro/record com matrícula crua ou tipo sensível")
    void verifiesDiscriminationAgainstRawMatriculaOutsideVerification() {
        JavaClasses violatorClasses = new ClassFileImporter()
                .importClasses(
                        SyntheticRawMatriculaFieldViolator.class,
                        SyntheticRawMatriculaConstructorViolator.class,
                        SyntheticRawMatriculaRecordViolator.class,
                        SyntheticRawMatriculaTypeFieldViolator.class);

        assertThatThrownBy(() -> NO_RAW_MATRICULA_OUTSIDE_VERIFICATION.check(violatorClasses))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("was violated")
                .hasMessageContaining("SyntheticRawMatriculaFieldViolator")
                .hasMessageContaining("SyntheticRawMatriculaConstructorViolator")
                .hasMessageContaining("SyntheticRawMatriculaRecordViolator")
                .hasMessageContaining("SyntheticRawMatriculaTypeFieldViolator");
    }

    @Test
    @DisplayName("Prova de discriminação: NO_MATRICULA_OR_DIGEST_IN_LOGGER falha quando método ou classe com dados sensíveis chama logger")
    void verifiesDiscriminationAgainstMatriculaOrDigestInLogger() {
        JavaClasses violatorClasses = new ClassFileImporter()
                .importClasses(
                        SyntheticLoggerViolator.class,
                        SyntheticDigestLoggerViolator.class,
                        SyntheticFieldLoggerViolator.class);

        assertThatThrownBy(() -> NO_MATRICULA_OR_DIGEST_IN_LOGGER.check(violatorClasses))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("was violated")
                .hasMessageContaining("SyntheticLoggerViolator")
                .hasMessageContaining("SyntheticDigestLoggerViolator")
                .hasMessageContaining("SyntheticFieldLoggerViolator");
    }

    @Test
    @DisplayName("Prova de discriminação: NO_MATRICULA_OR_DIGEST_IN_HTTP_RESPONSE falha quando record de resposta (direto, aninhado ou genérico) expõe matrícula, digest ou tipo sensível")
    void verifiesDiscriminationAgainstMatriculaOrDigestInHttpResponse() {
        JavaClasses violatorClasses = new ClassFileImporter()
                .importClasses(
                        SyntheticResponseControllerViolator.class,
                        SyntheticResponseRecordViolator.class,
                        SyntheticNestedResponseControllerViolator.class,
                        SyntheticNestedOuterResponseRecord.class,
                        SyntheticNestedSensitiveRecord.class,
                        SyntheticTypeResponseControllerViolator.class,
                        SyntheticTypeResponseRecord.class);

        assertThatThrownBy(() -> NO_MATRICULA_OR_DIGEST_IN_HTTP_RESPONSE.check(violatorClasses))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("was violated")
                .hasMessageContaining("SyntheticResponseRecordViolator")
                .hasMessageContaining("SyntheticNestedSensitiveRecord")
                .hasMessageContaining("SyntheticTypeResponseRecord");
    }

    @Test
    @DisplayName("Prova de discriminação: NO_MATRICULA_IN_CONTROLLER_URL_OR_PARAMS falha quando controlador declara path/query param ou URI template com matrícula")
    void verifiesDiscriminationAgainstMatriculaInControllerUrlOrParams() {
        JavaClasses violatorClasses = new ClassFileImporter()
                .importClasses(
                        SyntheticControllerWithQueryParamViolator.class,
                        SyntheticControllerWithPathVarViolator.class,
                        SyntheticControllerWithPathUriTemplateViolator.class);

        assertThatThrownBy(() -> NO_MATRICULA_IN_CONTROLLER_URL_OR_PARAMS.check(violatorClasses))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("was violated")
                .hasMessageContaining("SyntheticControllerWithQueryParamViolator")
                .hasMessageContaining("SyntheticControllerWithPathVarViolator")
                .hasMessageContaining("SyntheticControllerWithPathUriTemplateViolator");
    }

    // ── Provas Explícitas de Não Vacuidade ─────────────────────────────────────

    @Test
    @DisplayName("Prova de não vacuidade: regra 1 analisa classes de produção fora de verification")
    void verifiesNonVacuityRule1() {
        JavaClasses productionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("br.com.betufu");

        long analyzedClasses = productionClasses.stream()
                .filter(c -> !c.getPackageName().startsWith("br.com.betufu.verification"))
                .count();

        assertThat(analyzedClasses).isGreaterThanOrEqualTo(30);
        NO_RAW_MATRICULA_OUTSIDE_VERIFICATION.check(productionClasses);
    }

    @Test
    @DisplayName("Prova de não vacuidade: regra 2 analisa chamadas a logger no repositório inteiro")
    void verifiesNonVacuityRule2() {
        JavaClasses productionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("br.com.betufu");

        assertThat(productionClasses.size()).isGreaterThanOrEqualTo(30);
        NO_MATRICULA_OR_DIGEST_IN_LOGGER.check(productionClasses);
    }

    @Test
    @DisplayName("Prova de não vacuidade: regra 3 analisa controladores e records estruturais de resposta HTTP")
    void verifiesNonVacuityRule3() {
        JavaClasses productionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("br.com.betufu");

        Set<JavaClass> controllers = new HashSet<>();
        Set<JavaClass> responseRecords = new HashSet<>();
        for (JavaClass clazz : productionClasses) {
            if (isControllerClass(clazz)) {
                controllers.add(clazz);
                responseRecords.addAll(extractResponseRecords(clazz));
            }
        }

        assertThat(controllers).isNotEmpty();
        assertThat(responseRecords).isNotEmpty();
        NO_MATRICULA_OR_DIGEST_IN_HTTP_RESPONSE.check(productionClasses);
    }

    @Test
    @DisplayName("Prova de não vacuidade: regra 4 analisa controladores HTTP para rotas e parâmetros")
    void verifiesNonVacuityRule4() {
        JavaClasses productionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("br.com.betufu");

        long controllerCount = productionClasses.stream()
                .filter(PrivacyArchitectureTest::isControllerClass)
                .count();

        assertThat(controllerCount).isGreaterThanOrEqualTo(3);
        NO_MATRICULA_IN_CONTROLLER_URL_OR_PARAMS.check(productionClasses);
    }

    // ── Auxiliares de Extração Estrutural de Records ───────────────────────────

    private static Set<JavaClass> extractResponseRecords(JavaClass controllerClass) {
        Set<Class<?>> recordClasses = new LinkedHashSet<>();
        try {
            Class<?> reflectedController = controllerClass.reflect();
            for (Method method : reflectedController.getDeclaredMethods()) {
                if (isEndpointOrPublic(method)) {
                    Type returnType = method.getGenericReturnType();
                    unwrapReflectedTypeToRecords(returnType, recordClasses, new HashSet<>());
                }
            }
        } catch (Exception ignored) {
            for (JavaMethod jm : controllerClass.getMethods()) {
                if (isEndpointOrPublic(jm)) {
                    try {
                        Method rm = jm.reflect();
                        unwrapReflectedTypeToRecords(rm.getGenericReturnType(), recordClasses, new HashSet<>());
                    } catch (Exception ignored2) {
                    }
                }
            }
        }

        Set<Class<?>> allRecordClasses = new LinkedHashSet<>(recordClasses);
        Set<Class<?>> visited = new HashSet<>();
        List<Class<?>> queue = new ArrayList<>(recordClasses);
        while (!queue.isEmpty()) {
            Class<?> current = queue.remove(0);
            if (current != null && visited.add(current)) {
                Set<Class<?>> nested = new LinkedHashSet<>();
                collectNestedRecordClasses(current, nested);
                for (Class<?> n : nested) {
                    if (allRecordClasses.add(n)) {
                        queue.add(n);
                    }
                }
            }
        }

        Set<JavaClass> result = new LinkedHashSet<>();
        for (Class<?> clazz : allRecordClasses) {
            try {
                JavaClasses imported = new ClassFileImporter().importClasses(clazz);
                JavaClass jc = imported.get(clazz);
                if (jc != null) {
                    result.add(jc);
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private static void unwrapReflectedTypeToRecords(
            Type type,
            Set<Class<?>> collector,
            Set<Type> visited) {
        if (type == null || !visited.add(type)) {
            return;
        }
        if (type instanceof Class<?> clazz) {
            if (clazz.isRecord() && !clazz.isPrimitive() && !clazz.getName().startsWith("java.")) {
                collector.add(clazz);
            }
            if (clazz.isArray()) {
                unwrapReflectedTypeToRecords(clazz.getComponentType(), collector, visited);
            }
        } else if (type instanceof ParameterizedType pType) {
            unwrapReflectedTypeToRecords(pType.getRawType(), collector, visited);
            for (Type arg : pType.getActualTypeArguments()) {
                unwrapReflectedTypeToRecords(arg, collector, visited);
            }
        } else if (type instanceof WildcardType wType) {
            for (Type upper : wType.getUpperBounds()) {
                unwrapReflectedTypeToRecords(upper, collector, visited);
            }
            for (Type lower : wType.getLowerBounds()) {
                unwrapReflectedTypeToRecords(lower, collector, visited);
            }
        } else if (type instanceof GenericArrayType gType) {
            unwrapReflectedTypeToRecords(gType.getGenericComponentType(), collector, visited);
        }
    }

    private static void collectNestedRecordClasses(Class<?> recordClass, Set<Class<?>> nested) {
        if (recordClass == null) {
            return;
        }
        if (recordClass.isRecord()) {
            for (RecordComponent rc : recordClass.getRecordComponents()) {
                unwrapReflectedTypeToRecords(rc.getGenericType(), nested, new HashSet<>());
            }
        }
        for (Field field : recordClass.getDeclaredFields()) {
            if (field.isEnumConstant()) {
                continue;
            }
            unwrapReflectedTypeToRecords(field.getGenericType(), nested, new HashSet<>());
        }
        for (Constructor<?> constructor : recordClass.getDeclaredConstructors()) {
            for (Type paramType : constructor.getGenericParameterTypes()) {
                unwrapReflectedTypeToRecords(paramType, nested, new HashSet<>());
            }
        }
    }

    private static void checkResponseRecordStructure(
            JavaClass recordClass, JavaClass controllerClass, ConditionEvents events) {
        for (JavaField field : recordClass.getAllFields()) {
            if (isEnumConstant(field, recordClass)) {
                continue;
            }
            if (isRawMatriculaOrDigestVocabulary(field.getName())) {
                String message = String.format(
                        "HTTP response record %s (in controller %s) declares sensitive field '%s'",
                        recordClass.getName(), controllerClass.getSimpleName(), field.getName());
                events.add(SimpleConditionEvent.violated(controllerClass, message));
            }
            if (isSensitiveType(field.getRawType())) {
                String message = String.format(
                        "HTTP response record %s (in controller %s) declares sensitive field '%s' of type '%s'",
                        recordClass.getName(), controllerClass.getSimpleName(), field.getName(), field.getRawType().getName());
                events.add(SimpleConditionEvent.violated(controllerClass, message));
            }
        }

        for (JavaConstructor constructor : recordClass.getConstructors()) {
            for (JavaParameter parameter : constructor.getParameters()) {
                String paramName = getParameterName(parameter);
                if (isRawMatriculaOrDigestVocabulary(paramName)) {
                    String message = String.format(
                            "HTTP response record %s (in controller %s) declares constructor parameter '%s' with sensitive vocabulary",
                            recordClass.getName(), controllerClass.getSimpleName(), paramName);
                    events.add(SimpleConditionEvent.violated(controllerClass, message));
                }
                if (isSensitiveType(parameter.getRawType())) {
                    String message = String.format(
                            "HTTP response record %s (in controller %s) declares constructor parameter '%s' of sensitive type '%s'",
                            recordClass.getName(), controllerClass.getSimpleName(), paramName, parameter.getRawType().getName());
                    events.add(SimpleConditionEvent.violated(controllerClass, message));
                }
            }
        }

        try {
            Class<?> reflected = recordClass.reflect();
            if (reflected.isRecord()) {
                for (RecordComponent rc : reflected.getRecordComponents()) {
                    if (isRawMatriculaOrDigestVocabulary(rc.getName())) {
                        String message = String.format(
                                "HTTP response record %s (in controller %s) declares record component '%s' with sensitive vocabulary",
                                recordClass.getName(), controllerClass.getSimpleName(), rc.getName());
                        events.add(SimpleConditionEvent.violated(controllerClass, message));
                    }
                    if (isSensitiveTypeName(rc.getType().getName())) {
                        String message = String.format(
                                "HTTP response record %s (in controller %s) declares record component '%s' of sensitive type '%s'",
                                recordClass.getName(), controllerClass.getSimpleName(), rc.getName(), rc.getType().getName());
                        events.add(SimpleConditionEvent.violated(controllerClass, message));
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isControllerClass(JavaClass javaClass) {
        return javaClass.isAnnotatedWith(RestController.class)
                || javaClass.isAnnotatedWith(Controller.class)
                || javaClass.isAnnotatedWith(RestControllerAdvice.class)
                || javaClass.isAnnotatedWith(ControllerAdvice.class);
    }

    private static boolean isEndpointOrPublic(Method method) {
        if (method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class)
                || method.isAnnotationPresent(PatchMapping.class)
                || method.isAnnotationPresent(RequestMapping.class)
                || method.isAnnotationPresent(ExceptionHandler.class)) {
            return true;
        }
        return Modifier.isPublic(method.getModifiers())
                && !method.getName().equals("equals")
                && !method.getName().equals("hashCode")
                && !method.getName().equals("toString")
                && !method.getName().equals("getClass")
                && !method.getName().startsWith("notify")
                && !method.getName().startsWith("wait");
    }

    private static boolean isEndpointOrPublic(JavaMethod method) {
        if (method.isAnnotatedWith(GetMapping.class)
                || method.isAnnotatedWith(PostMapping.class)
                || method.isAnnotatedWith(PutMapping.class)
                || method.isAnnotatedWith(DeleteMapping.class)
                || method.isAnnotatedWith(PatchMapping.class)
                || method.isAnnotatedWith(RequestMapping.class)
                || method.isAnnotatedWith(ExceptionHandler.class)) {
            return true;
        }
        return method.getModifiers().contains(JavaModifier.PUBLIC)
                && !method.getName().equals("equals")
                && !method.getName().equals("hashCode")
                && !method.getName().equals("toString")
                && !method.getName().equals("getClass")
                && !method.getName().startsWith("notify")
                && !method.getName().startsWith("wait");
    }

    // ── Auxiliares de Inspeção e Vocabulário ───────────────────────────────────

    private static boolean isPathVariableParameter(JavaParameter parameter) {
        if (parameter.isAnnotatedWith(PathVariable.class)) {
            return true;
        }
        for (JavaAnnotation<?> annotation : parameter.getAnnotations()) {
            if (annotation.getRawType().getName().equals("org.springframework.web.bind.annotation.PathVariable")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRequestParamParameter(JavaParameter parameter) {
        if (parameter.isAnnotatedWith(RequestParam.class)) {
            return true;
        }
        for (JavaAnnotation<?> annotation : parameter.getAnnotations()) {
            if (annotation.getRawType().getName().equals("org.springframework.web.bind.annotation.RequestParam")) {
                return true;
            }
        }
        return false;
    }

    private static String getParameterName(JavaParameter parameter) {
        try {
            if (parameter.getOwner() instanceof JavaMethod javaMethod) {
                Method reflectedMethod = javaMethod.reflect();
                Parameter[] reflectedParams = reflectedMethod.getParameters();
                if (parameter.getIndex() < reflectedParams.length) {
                    return reflectedParams[parameter.getIndex()].getName();
                }
            } else if (parameter.getOwner() instanceof JavaConstructor javaConstructor) {
                Constructor<?> reflectedConstructor = javaConstructor.reflect();
                Parameter[] reflectedParams = reflectedConstructor.getParameters();
                if (parameter.getIndex() < reflectedParams.length) {
                    return reflectedParams[parameter.getIndex()].getName();
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static boolean isEnumConstant(JavaField field, JavaClass javaClass) {
        if (javaClass.isEnum() && field.getRawType().getName().equals(javaClass.getName())) {
            return true;
        }
        return field.getOwner().isEnum() && field.getRawType().getName().equals(field.getOwner().getName());
    }

    private static boolean isRawMatriculaVocabulary(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("status") || lower.contains("version") || lower.contains("action")
                || lower.contains("outcome") || lower.contains("count") || lower.contains("total")
                || lower.contains("audit") || lower.contains("event") || lower.contains("hmac")
                || lower.contains("code") || lower.contains("title") || lower.contains("detail")) {
            return false;
        }
        return lower.contains("matricula") || lower.contains("enrollment");
    }

    private static boolean isRawMatriculaType(JavaClass type) {
        if (type == null) {
            return false;
        }
        return isRawMatriculaTypeName(type.getName());
    }

    private static boolean isRawMatriculaTypeName(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return false;
        }
        if (typeName.startsWith("java.") || typeName.startsWith("jakarta.") || typeName.startsWith("org.springframework.")) {
            return false;
        }
        String lower = typeName.toLowerCase(Locale.ROOT);
        if (lower.contains("status") || lower.contains("configuration") || lower.contains("properties")
                || lower.contains("test") || lower.contains("audit") || lower.contains("event")) {
            return false;
        }
        return typeName.startsWith("br.com.betufu.verification")
                || lower.contains("matriculahmac")
                || lower.contains("matriculanormalizer")
                || lower.contains("rawmatricula");
    }

    private static boolean isSensitiveType(JavaClass type) {
        if (type == null) {
            return false;
        }
        return isSensitiveTypeName(type.getName());
    }

    private static boolean isSensitiveTypeName(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return false;
        }
        if (typeName.startsWith("java.") || typeName.startsWith("jakarta.") || typeName.startsWith("org.springframework.")) {
            return false;
        }
        String lower = typeName.toLowerCase(Locale.ROOT);
        if (lower.contains("status") || lower.contains("configuration") || lower.contains("properties")
                || lower.contains("test") || lower.contains("audit") || lower.contains("event")) {
            return false;
        }
        return typeName.startsWith("br.com.betufu.verification")
                || lower.contains("matricula")
                || lower.contains("digest")
                || lower.contains("hmac");
    }

    private static boolean isLoggerType(JavaClass type) {
        String name = type.getName();
        return name.equals("org.slf4j.Logger")
                || name.equals("org.apache.commons.logging.Log")
                || name.equals("java.util.logging.Logger")
                || name.equals("org.apache.logging.log4j.Logger")
                || name.equals("org.jboss.logging.Logger")
                || name.equals("org.springframework.core.log.LogAccessor")
                || type.isAssignableTo(Logger.class);
    }

    private static boolean isSensitiveParamName(String name) {
        return isRawMatriculaOrDigestVocabulary(name);
    }

    private static boolean isRawMatriculaOrDigestVocabulary(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("status") || lower.contains("version") || lower.contains("action")
                || lower.contains("outcome") || lower.contains("count") || lower.contains("total")
                || lower.contains("audit") || lower.contains("event")
                || lower.contains("code") || lower.contains("title") || lower.contains("detail")) {
            return false;
        }
        return lower.contains("matricula") || lower.contains("enrollment") || lower.contains("digest") || lower.contains("hmac");
    }

    private static boolean isSensitiveParamOrPath(String name) {
        return isRawMatriculaOrDigestVocabulary(name);
    }

    private static String getPathVariableName(JavaParameter parameter) {
        for (JavaAnnotation<?> annotation : parameter.getAnnotations()) {
            if (annotation.getRawType().isAssignableTo(PathVariable.class)
                    || annotation.getRawType().getName().equals("org.springframework.web.bind.annotation.PathVariable")) {
                Object value = annotation.get("value").orElse(null);
                if (value instanceof String s && !s.isBlank()) {
                    return s;
                }
                Object name = annotation.get("name").orElse(null);
                if (name instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
        }
        return getParameterName(parameter);
    }

    private static String getRequestParamName(JavaParameter parameter) {
        for (JavaAnnotation<?> annotation : parameter.getAnnotations()) {
            if (annotation.getRawType().isAssignableTo(RequestParam.class)
                    || annotation.getRawType().getName().equals("org.springframework.web.bind.annotation.RequestParam")) {
                Object value = annotation.get("value").orElse(null);
                if (value instanceof String s && !s.isBlank()) {
                    return s;
                }
                Object name = annotation.get("name").orElse(null);
                if (name instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
        }
        return getParameterName(parameter);
    }

    private static void checkClassLevelMappingPaths(JavaClass javaClass, ConditionEvents events) {
        for (JavaAnnotation<?> annotation : javaClass.getAnnotations()) {
            String typeName = annotation.getRawType().getName();
            if (typeName.equals("org.springframework.web.bind.annotation.RequestMapping")) {
                checkPathValues(annotation.get("value").orElse(null), null, javaClass, events);
                checkPathValues(annotation.get("path").orElse(null), null, javaClass, events);
            }
        }
    }

    private static void checkMappingPaths(JavaMethod method, JavaClass javaClass, ConditionEvents events) {
        for (JavaAnnotation<?> annotation : method.getAnnotations()) {
            String typeName = annotation.getRawType().getName();
            if (typeName.equals("org.springframework.web.bind.annotation.GetMapping")
                    || typeName.equals("org.springframework.web.bind.annotation.PostMapping")
                    || typeName.equals("org.springframework.web.bind.annotation.PutMapping")
                    || typeName.equals("org.springframework.web.bind.annotation.DeleteMapping")
                    || typeName.equals("org.springframework.web.bind.annotation.PatchMapping")
                    || typeName.equals("org.springframework.web.bind.annotation.RequestMapping")) {

                checkPathValues(annotation.get("value").orElse(null), method, javaClass, events);
                checkPathValues(annotation.get("path").orElse(null), method, javaClass, events);
            }
        }
    }

    private static void checkPathValues(Object pathObj, JavaMethod method, JavaClass javaClass, ConditionEvents events) {
        if (pathObj instanceof String[] paths) {
            for (String path : paths) {
                checkSinglePath(path, method, javaClass, events);
            }
        } else if (pathObj instanceof String path) {
            checkSinglePath(path, method, javaClass, events);
        }
    }

    private static void checkSinglePath(String path, JavaMethod method, JavaClass javaClass, ConditionEvents events) {
        if (path == null) {
            return;
        }
        if (path.contains("{") && path.contains("}")) {
            var matcher = Pattern.compile("\\{([^}]+)\\}").matcher(path);
            while (matcher.find()) {
                String varName = matcher.group(1);
                if (varName.contains(":")) {
                    varName = varName.substring(0, varName.indexOf(':'));
                }
                if (isSensitiveParamOrPath(varName)) {
                    String methodDesc = method != null ? method.getName() : "<class-level>";
                    String message = String.format(
                            "Controller %s.%s declares path URI template variable '{%s}' in path '%s'",
                            javaClass.getSimpleName(), methodDesc, varName, path);
                    events.add(SimpleConditionEvent.violated(javaClass, message));
                }
            }
        }
    }

    // ── Fixtures Sintéticas Violadoras (Provas de Discriminação) ──────────────

    static class SyntheticRawMatriculaFieldViolator {
        @SuppressWarnings("unused")
        private String matricula;
    }

    static class SyntheticRawMatriculaConstructorViolator {
        @SuppressWarnings("unused")
        private final String value;

        public SyntheticRawMatriculaConstructorViolator(String matricula) {
            this.value = matricula;
        }
    }

    record SyntheticRawMatriculaRecordViolator(String matricula) {}

    static class SyntheticRawMatriculaTypeFieldViolator {
        @SuppressWarnings("unused")
        private br.com.betufu.verification.internal.MatriculaHmac unauthorizedHmac;
    }

    static class SyntheticLoggerViolator {
        private static final Logger log = LoggerFactory.getLogger(SyntheticLoggerViolator.class);

        public void logMatricula(String rawMatricula) {
            log.info("Matricula recebida: {}", rawMatricula);
        }
    }

    static class SyntheticDigestLoggerViolator {
        private static final Logger log = LoggerFactory.getLogger(SyntheticDigestLoggerViolator.class);

        public void logDigest(br.com.betufu.verification.internal.MatriculaHmac hmac) {
            log.debug("Digest calculado: {}", hmac);
        }
    }

    static class SyntheticFieldLoggerViolator {
        private static final Logger log = LoggerFactory.getLogger(SyntheticFieldLoggerViolator.class);
        @SuppressWarnings("unused")
        private String matricula;

        public void performAction() {
            log.info("Performing action");
        }
    }

    record SyntheticNestedSensitiveRecord(String rawMatricula) {}

    record SyntheticResponseRecordViolator(
            UUID identityId,
            String matricula,
            byte[] digest) {}

    record SyntheticNestedOuterResponseRecord(
            UUID identityId,
            SyntheticNestedSensitiveRecord nested) {}

    record SyntheticTypeResponseRecord(
            UUID identityId,
            br.com.betufu.verification.internal.MatriculaHmac hmac) {}

    @RestController
    static class SyntheticResponseControllerViolator {
        @GetMapping("/api/test-violation-direct")
        public ResponseEntity<SyntheticResponseRecordViolator> getViolation() {
            return ResponseEntity.ok(
                    new SyntheticResponseRecordViolator(UUID.randomUUID(), "12345ABC", new byte[32]));
        }
    }

    @RestController
    static class SyntheticNestedResponseControllerViolator {
        @GetMapping("/api/test-violation-nested")
        public ResponseEntity<Optional<List<SyntheticNestedOuterResponseRecord>>> getNestedViolation() {
            return ResponseEntity.ok(Optional.empty());
        }
    }

    @RestController
    static class SyntheticTypeResponseControllerViolator {
        @GetMapping("/api/test-violation-type")
        public ResponseEntity<SyntheticTypeResponseRecord> getTypeViolation() {
            return ResponseEntity.ok(null);
        }
    }

    @RestController
    static class SyntheticControllerWithQueryParamViolator {
        @GetMapping("/api/v1/test-search")
        public String search(@RequestParam("matricula") String matricula) {
            return "ok";
        }
    }

    @RestController
    static class SyntheticControllerWithPathVarViolator {
        @GetMapping("/api/v1/test-matriculas/{matricula}")
        public String getByMatricula(@PathVariable("matricula") String matricula) {
            return "ok";
        }
    }

    @RestController
    static class SyntheticControllerWithPathUriTemplateViolator {
        @GetMapping("/api/v1/users/{rawMatricula}/details")
        public String getDetails(@PathVariable("rawMatricula") String id) {
            return "ok";
        }
    }
}
