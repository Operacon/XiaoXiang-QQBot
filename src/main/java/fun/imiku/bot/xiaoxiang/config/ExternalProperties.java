package fun.imiku.bot.xiaoxiang.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ExternalProperties {
    private final XiaoXiangProperties xxProperties;
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();


    @Getter
    private final Stats stats = new Stats();
    @Getter
    private final Common common = new Common();

    @Data
    public static class Stats {
        /**
         * 词云使用，每日群聊历史最大条数（受过长消息影响，实际最大条数会略小于配置值）
         */
        private long maxHistoryCount = 20000;
        /**
         * 词云使用，超过此长度的消息不会被放进历史
         */
        private long maxMessageLength = 140;
    }

    @Data
    public static class Common {
        /**
         * 发送消息时，随机等待的最多时间。可能有助于风控管理。只有 XXBot 实现的发送函数支持本配置
         */
        private long sendRandomAwaitMax = 700;

        /**
         * 发送消息时，随机等待的最少时间。可能有助于风控管理。只有 XXBot 实现的发送函数支持本配置
         */
        private long  sendRandomAwaitMin = 100;
    }

    public ExternalProperties(XiaoXiangProperties xxProperties) {
        this.xxProperties = xxProperties;
    }

    @PostConstruct
    public void fillFromFile() {
        Path configPath = prepareConfigPath();

        try {
            if (Files.notExists(configPath)) {
                writeCurrentState(configPath);
                log.info("外部配置文件不存在，已创建：{}", configPath);
                return;
            }

            JsonNode rootNode;
            try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                rootNode = objectMapper.readTree(reader);
            }

            if (!rootNode.isObject()) {
                throw new IllegalArgumentException("外部配置文件必须是 json，且不支持注释：" + configPath);
            }
            applyJsonToCurrentObject(this, rootNode);
            // 写入一次，这样代码变更引入的新变化会写入文件
            saveToFile();
            log.info("已加载外部配置文件：{}", configPath);
        } catch (Exception ex) {
            throw new IllegalStateException("外部配置文件解析出错，请检查" + configPath, ex);
        }
    }

    /**
     * 修改属性后，持久化到文件
     */
    public void saveToFile() {
        Path configPath = prepareConfigPath();

        try {
            writeCurrentState(configPath);
            log.info("已更新配置值到外部配置文件：{}", configPath);
        } catch (Exception ex) {
            throw new IllegalStateException("更新配置值到外部配置文件失败 " + configPath, ex);
        }
    }

    private void writeCurrentState(Path configPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(
                configPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        )) {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(writer, this);
        }
    }

    private void applyJsonToCurrentObject(Object target, JsonNode node)
            throws IntrospectionException, InvocationTargetException, IllegalAccessException {
        Map<String, PropertyDescriptor> propertiesByName = describeProperties(target.getClass());

        for (String name : node.propertyNames()) {
            PropertyDescriptor property = propertiesByName.get(name);
            if (property == null) {
                continue;
            }

            Method getter = property.getReadMethod();
            if (getter == null) {
                continue;
            }

            JsonNode childNode = node.get(name);
            if (childNode == null) {
                continue;
            }

            Object currentValue = getter.invoke(target);
            Class<?> propertyType = property.getPropertyType();

            if (childNode.isObject() && currentValue != null && shouldRecursiveUpdate(propertyType)) {
                applyJsonToCurrentObject(currentValue, childNode);
                continue;
            }

            Type targetType = resolveTargetType(property, getter);
            Object convertedValue = objectMapper.readValue(
                    objectMapper.treeAsTokens(childNode),
                    objectMapper.getTypeFactory().constructType(targetType)
            );

            if (setWritableProperty(target, property, convertedValue)) {
                continue;
            }
            if (updateMutableContainer(currentValue, convertedValue)) {
                continue;
            }

            throw new IllegalStateException(
                    "External config property '" + name + "' is immutable and cannot be updated from file. " +
                            "Use a writable property, a mutable nested object, or a mutable collection."
            );
        }
    }

    private Map<String, PropertyDescriptor> describeProperties(Class<?> type) throws IntrospectionException {
        PropertyDescriptor[] descriptors = Introspector.getBeanInfo(type, Object.class).getPropertyDescriptors();
        Map<String, PropertyDescriptor> result = new HashMap<>(descriptors.length);
        for (PropertyDescriptor descriptor : descriptors) {
            result.put(descriptor.getName(), descriptor);
        }
        return result;
    }

    private Type resolveTargetType(PropertyDescriptor property, Method getter) {
        Method setter = property.getWriteMethod();
        if (setter != null && setter.getGenericParameterTypes().length == 1) {
            return setter.getGenericParameterTypes()[0];
        }
        return getter.getGenericReturnType();
    }

    private boolean setWritableProperty(Object target, PropertyDescriptor property, Object value)
            throws InvocationTargetException, IllegalAccessException {
        Method setter = property.getWriteMethod();
        if (setter == null) {
            return false;
        }
        setter.invoke(target, value);
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean updateMutableContainer(Object currentValue, Object convertedValue) {
        if (currentValue instanceof Collection<?> currentCollection && convertedValue instanceof Collection<?> newCollection) {
            Collection<Object> targetCollection = (Collection<Object>) currentCollection;
            targetCollection.clear();
            targetCollection.addAll(newCollection);
            return true;
        }

        if (currentValue instanceof Map<?, ?> currentMap && convertedValue instanceof Map<?, ?> newMap) {
            Map<Object, Object> targetMap = (Map<Object, Object>) currentMap;
            targetMap.clear();
            targetMap.putAll(newMap);
            return true;
        }

        return false;
    }

    private boolean shouldRecursiveUpdate(Class<?> javaClass) {
        if (javaClass.isPrimitive() || javaClass.isEnum() || javaClass.isArray()) {
            return false;
        }
        return !CharSequence.class.isAssignableFrom(javaClass) &&
                !Number.class.isAssignableFrom(javaClass) &&
                javaClass != Boolean.class &&
                javaClass != Character.class &&
                !Map.class.isAssignableFrom(javaClass) &&
                !Collection.class.isAssignableFrom(javaClass);
    }

    private Path prepareConfigPath() {
        Path configPath = resolveConfigPath(xxProperties.getConfig().getConfigFile());
        ensureParentDirectory(configPath);

        if (Files.exists(configPath) && !Files.isRegularFile(configPath)) {
            throw new IllegalStateException("外部配置文件非正常，检查配置 " + configPath);
        }

        return configPath;
    }

    private Path resolveConfigPath(String rawPath) {
        String path = rawPath.trim();
        if (path.isBlank()) {
            throw new IllegalArgumentException("必须配置外部配置文件");
        }

        String userHome = System.getProperty("user.home");
        String expanded;
        if ("~".equals(path)) {
            expanded = userHome;
        } else if (path.startsWith("~/") || path.startsWith("~\\")) {
            expanded = userHome + path.substring(1);
        } else {
            expanded = path;
        }

        return Paths.get(expanded).toAbsolutePath().normalize();
    }

    private void ensureParentDirectory(Path configPath) {
        Path parent = configPath.getParent();
        if (parent == null) {
            return;
        }

        try {
            Files.createDirectories(parent);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create parent directory for " + configPath, ex);
        }
    }
}
