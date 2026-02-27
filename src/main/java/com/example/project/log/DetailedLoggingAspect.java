package com.example.project.log;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

@Aspect
@Component
public class DetailedLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(DetailedLoggingAspect.class);
    private static final ThreadLocal<Integer> callDepth = ThreadLocal.withInitial(() -> 0);

    // ------------------------- POINTCUTS -------------------------
    @Pointcut("execution(public * com.example.project.service..*(..))")
    public void serviceLayer() {}

    @Pointcut("execution(public * com.example.project.controller..*(..)) || execution(public * com.example.project.rest..*(..))")
    public void controllerLayer() {}

    @Pointcut("serviceLayer() || controllerLayer()")
    public void applicationLayers() {}

    // ------------------------- ADVICE -------------------------
    @Around("applicationLayers()")
    public Object logMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = method.getName();

        int depth = callDepth.get();
        callDepth.set(depth + 1);
        String indent = "  ".repeat(depth);

        long startTime = System.currentTimeMillis();
        Object result = null;

        try {
            logMethodEntry(joinPoint, indent, className, methodName);
            result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;
            logMethodSuccess(result, indent, className, methodName, executionTime);
            return result;

        } catch (Throwable e) {
            long executionTime = System.currentTimeMillis() - startTime;
            logMethodError(e, indent, className, methodName, executionTime);
            throw e;

        } finally {
            logMethodExit(indent, className, methodName);
            callDepth.set(depth);
        }
    }

    private void logMethodEntry(JoinPoint joinPoint, String indent, String className, String methodName) {
        log.info("{}┌──▶ ВХОД: {}.{}", indent, className, methodName);

        Object[] args = joinPoint.getArgs();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();

        if (args != null && args.length > 0) {
            log.info("{}│ Параметры:", indent);
            for (int i = 0; i < args.length; i++) {
                String paramName = (paramNames != null && i < paramNames.length) ? paramNames[i] : "arg" + i;
                Object arg = args[i];

                if (arg == null) {
                    log.info("{}│   • {} = null", indent, paramName);
                } else if (arg instanceof byte[]) {
                    log.info("{}│   • {} = byte[{}] (скрыто)", indent, paramName, ((byte[]) arg).length);
                } else if (arg instanceof MultipartFile file) {
                    log.info("{}│   • {} = MultipartFile: {} ({} bytes)",
                            indent, paramName, file.getOriginalFilename(), file.getSize());
                } else if (isSensitiveData(paramName, arg)) {
                    log.info("{}│   • {} = [PROTECTED]", indent, paramName);
                } else if (isSimpleType(arg)) {
                    log.info("{}│   • {} = {}", indent, paramName, arg);
                } else {
                    log.info("{}│   • {} = {} {}", indent, paramName, arg.getClass().getSimpleName(), arg);
                }
            }
        } else {
            log.info("{}│ Параметры: нет", indent);
        }
    }

    private void logMethodSuccess(Object result, String indent, String className,
                                  String methodName, long executionTime) {
        log.info("{}│ ✓ УСПЕШНО: {}.{}", indent, className, methodName);

        if (result != null) {
            if (result instanceof byte[]) {
                log.info("{}│ Результат: byte[{}] (скрыто)", indent, ((byte[]) result).length);
            } else if (result instanceof MultipartFile file) {
                log.info("{}│ Результат: MultipartFile: {} ({} bytes)",
                        indent, file.getOriginalFilename(), file.getSize());
            } else if (isSensitiveData("result", result)) {
                log.info("{}│ Результат: {} [PROTECTED]", indent, result.getClass().getSimpleName());
            } else if (isSimpleType(result)) {
                log.info("{}│ Результат: {}", indent, result);
            } else {
                log.info("{}│ Результат: {} {}", indent, result.getClass().getSimpleName(), result);
            }
        } else {
            log.info("{}│ Результат: void/null", indent);
        }

        log.info("{}│ ⏱ Время: {} мс", indent, executionTime);

        if (executionTime > 1000) {
            log.warn("{}│ ⚠ ВНИМАНИЕ: медленное выполнение!", indent);
        }
    }

    private void logMethodError(Throwable e, String indent, String className,
                                String methodName, long executionTime) {
        log.error("{}│ ✗ ОШИБКА: {}.{}", indent, className, methodName);
        log.error("{}│ Исключение: {}", indent, e.getClass().getSimpleName());
        log.error("{}│ Сообщение: {}", indent, e.getMessage());
        log.error("{}│ ⏱ Время до ошибки: {} мс", indent, executionTime);
    }

    private void logMethodExit(String indent, String className, String methodName) {
        log.info("{}└──▶ ВЫХОД: {}.{}", indent, className, methodName);
    }

    private boolean isSensitiveData(String name, Object value) {
        if (value == null) return false;
        if (value instanceof byte[]) return false;
        if (value instanceof MultipartFile) return false;

        String lowerName = name.toLowerCase();
        String valueStr = value.toString().toLowerCase();

        return lowerName.contains("password")
                || lowerName.contains("token")
                || lowerName.contains("secret")
                || valueStr.contains("password")
                || valueStr.contains("token");
    }

    private boolean isSimpleType(Object obj) {
        return obj instanceof String
                || obj instanceof Number
                || obj instanceof Boolean
                || obj instanceof Character
                || obj.getClass().isPrimitive();
    }
    private void logValue(String indent, String paramName, Object arg) {
        if (arg == null) {
            log.info("{}│   • {} = null", indent, paramName);
        } else if (arg instanceof byte[]) {
            log.info("{}│   • {} = byte[{}] (скрыто)", indent, paramName, ((byte[]) arg).length);
        } else if (arg instanceof Collection<?> collection) {
            log.info("{}│   • {} = {} элементов (скрыто)", indent, paramName, collection.size());
        } else if (arg instanceof Map<?, ?> map) {
            log.info("{}│   • {} = {} элементов (скрыто)", indent, paramName, map.size());
        } else if (isSensitiveData(paramName, arg)) {
            log.info("{}│   • {} = [PROTECTED]", indent, paramName);
        } else if (isSimpleType(arg)) {
            log.info("{}│   • {} = {}", indent, paramName, arg);
        } else {
            log.info("{}│   • {} = {} {}", indent, paramName, arg.getClass().getSimpleName(), arg);
        }
    }
}