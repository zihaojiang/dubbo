/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.config.spring.beans.factory.annotation;

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.config.annotation.Service;
import org.apache.dubbo.config.spring.ServiceBean;
import org.apache.dubbo.config.spring.context.annotation.DubboClassPathBeanDefinitionScanner;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.config.*;
import org.springframework.beans.factory.support.*;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ConfigurationClassPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.*;

import java.util.*;

import static org.apache.dubbo.config.spring.util.ObjectUtils.of;
import static org.springframework.context.annotation.AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR;

/**
 * {@link Service} Annotation
 * {@link BeanDefinitionRegistryPostProcessor Bean Definition Registry Post Processor}
 *
 * @since 2.5.8
 */
public class ServiceAnnotationBeanPostProcessor implements BeanDefinitionRegistryPostProcessor, EnvironmentAware,
        ResourceLoaderAware, BeanClassLoaderAware {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * 要扫描的包的集合
     */
    private final Set<String> packagesToScan;

    private Environment environment;

    private ResourceLoader resourceLoader;

    private ClassLoader classLoader;

    public ServiceAnnotationBeanPostProcessor(String... packagesToScan) {
        this(Arrays.asList(packagesToScan));
    }

    public ServiceAnnotationBeanPostProcessor(Collection<String> packagesToScan) {
        this(new LinkedHashSet<String>(packagesToScan));
    }

    public ServiceAnnotationBeanPostProcessor(Set<String> packagesToScan) {
        this.packagesToScan = packagesToScan;
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        // 解析 packagesToScan 集合。因为，可能存在占位符
        Set<String> resolvedPackagesToScan = resolvePackagesToScan(packagesToScan);
        // 扫描 packagesToScan 包，创建对应的 Spring BeanDefinition 对象，从而创建 Dubbo Service Bean 对象。
        if (!CollectionUtils.isEmpty(resolvedPackagesToScan)) {
            registerServiceBeans(resolvedPackagesToScan, registry);
        } else {
            if (logger.isWarnEnabled()) {
                logger.warn("packagesToScan is empty , ServiceBean registry will be ignored!");
            }
        }
    }

    /**
     * Registers Beans whose classes was annotated {@link Service}
     *
     * @param packagesToScan The base packages to scan
     * @param registry       {@link BeanDefinitionRegistry}
     */
    @SuppressWarnings("Duplicates")
    private void registerServiceBeans(Set<String> packagesToScan, BeanDefinitionRegistry registry) {
        // 创建 DubboClassPathBeanDefinitionScanner 对象
        DubboClassPathBeanDefinitionScanner scanner = new DubboClassPathBeanDefinitionScanner(registry, environment, resourceLoader);
        // 获得 BeanNameGenerator 对象，并设置 beanNameGenerator 到 scanner 中
        BeanNameGenerator beanNameGenerator = resolveBeanNameGenerator(registry);
        scanner.setBeanNameGenerator(beanNameGenerator);
        // 设置过滤获得带有 @Service 注解的类
        scanner.addIncludeFilter(new AnnotationTypeFilter(Service.class));

        // 遍历 packagesToScan 数组
        for (String packageToScan : packagesToScan) {
            // Registers @Service Bean first
            // 执行扫描
            scanner.scan(packageToScan);
            // Finds all BeanDefinitionHolders of @Service whether @ComponentScan scans or not.
            // 创建每个在 packageToScan 扫描到的类，对应的 BeanDefinitionHolder 对象，返回 BeanDefinitionHolder 集合
            Set<BeanDefinitionHolder> beanDefinitionHolders = findServiceBeanDefinitionHolders(scanner, packageToScan, registry, beanNameGenerator);
            // 注册到 registry 中
            if (!CollectionUtils.isEmpty(beanDefinitionHolders)) {
                for (BeanDefinitionHolder beanDefinitionHolder : beanDefinitionHolders) {
                    registerServiceBean(beanDefinitionHolder, registry, scanner);
                }
                if (logger.isInfoEnabled()) {
                    logger.info(beanDefinitionHolders.size() + " annotated Dubbo's @Service Components { " + beanDefinitionHolders + " } were scanned under package[" + packageToScan + "]");
                }
            } else {
                if (logger.isWarnEnabled()) {
                    logger.warn("No Spring Bean annotating Dubbo's @Service was found under package[" + packageToScan + "]");
                }
            }
        }
    }

    /**
     * It'd better to use BeanNameGenerator instance that should reference
     * {@link ConfigurationClassPostProcessor#componentScanBeanNameGenerator},
     * thus it maybe a potential problem on bean name generation.
     *
     * @param registry {@link BeanDefinitionRegistry}
     * @return {@link BeanNameGenerator} instance
     * @see SingletonBeanRegistry
     * @see AnnotationConfigUtils#CONFIGURATION_BEAN_NAME_GENERATOR
     * @see ConfigurationClassPostProcessor#processConfigBeanDefinitions
     * @since 2.5.8
     */
    @SuppressWarnings("Duplicates")
    private BeanNameGenerator resolveBeanNameGenerator(BeanDefinitionRegistry registry) {
        BeanNameGenerator beanNameGenerator = null;
        // 如果是 SingletonBeanRegistry 类型，从中获得对应的 BeanNameGenerator Bean 对象
        if (registry instanceof SingletonBeanRegistry) {
            SingletonBeanRegistry singletonBeanRegistry = SingletonBeanRegistry.class.cast(registry);
            beanNameGenerator = (BeanNameGenerator) singletonBeanRegistry.getSingleton(AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR);
        }
        // 如果不存在，则创建 AnnotationBeanNameGenerator 对象
        if (beanNameGenerator == null) {
            if (logger.isInfoEnabled()) {
                logger.info("BeanNameGenerator bean can't be found in BeanFactory with name [" + CONFIGURATION_BEAN_NAME_GENERATOR + "]");
                logger.info("BeanNameGenerator will be a instance of " + AnnotationBeanNameGenerator.class.getName() + " , it maybe a potential problem on bean name generation.");
            }
            beanNameGenerator = new AnnotationBeanNameGenerator();
        }
        return beanNameGenerator;
    }

    /**
     * Finds a {@link Set} of {@link BeanDefinitionHolder BeanDefinitionHolders} whose bean type annotated
     * {@link Service} Annotation.
     *
     * @param scanner       {@link ClassPathBeanDefinitionScanner}
     * @param packageToScan pachage to scan
     * @param registry      {@link BeanDefinitionRegistry}
     * @return non-null
     * @since 2.5.8
     */
    @SuppressWarnings("Duplicates")
    private Set<BeanDefinitionHolder> findServiceBeanDefinitionHolders(
            ClassPathBeanDefinitionScanner scanner, String packageToScan, BeanDefinitionRegistry registry,
            BeanNameGenerator beanNameGenerator) {
        // 获得 packageToScan 包下符合条件的 BeanDefinition 集合
        Set<BeanDefinition> beanDefinitions = scanner.findCandidateComponents(packageToScan);

        // 创建 BeanDefinitionHolder 集合
        Set<BeanDefinitionHolder> beanDefinitionHolders = new LinkedHashSet<BeanDefinitionHolder>(beanDefinitions.size());
        // 遍历 beanDefinitions 数组
        for (BeanDefinition beanDefinition : beanDefinitions) {
            // 获得 Bean 的名字
            String beanName = beanNameGenerator.generateBeanName(beanDefinition, registry);
            // 创建 BeanDefinitionHolder 对象
            BeanDefinitionHolder beanDefinitionHolder = new BeanDefinitionHolder(beanDefinition, beanName);
            // 添加到 beanDefinitions 中
            beanDefinitionHolders.add(beanDefinitionHolder);
        }
        return beanDefinitionHolders;
    }

    /**
     * Registers {@link ServiceBean} from new annotated {@link Service} {@link BeanDefinition}
     *
     * @param beanDefinitionHolder
     * @param registry
     * @param scanner
     * @see ServiceBean
     * @see BeanDefinition
     */
    @SuppressWarnings("Duplicates")
    private void registerServiceBean(BeanDefinitionHolder beanDefinitionHolder, BeanDefinitionRegistry registry,
                                     DubboClassPathBeanDefinitionScanner scanner) {
        // 解析 Bean 的类
        Class<?> beanClass = resolveClass(beanDefinitionHolder);
        // 获得 @Service 注解
        Service service = AnnotationUtils.findAnnotation(beanClass, Service.class);
        // 获得 Service 接口
        Class<?> interfaceClass = resolveServiceInterfaceClass(beanClass, service);
        // 获得 Bean 的名字
        String annotatedServiceBeanName = beanDefinitionHolder.getBeanName();
        // 创建 AbstractBeanDefinition 对象
        AbstractBeanDefinition serviceBeanDefinition = buildServiceBeanDefinition(service, interfaceClass, annotatedServiceBeanName);

        // ServiceBean Bean name
        // 重新生成 Bean 的名字
        String beanName = generateServiceBeanName(service, interfaceClass, annotatedServiceBeanName);
        // 校验在 scanner 中，已经存在 beanName 。若不存在，则进行注册。
        if (scanner.checkCandidate(beanName, serviceBeanDefinition)) { // check duplicated candidate bean
            registry.registerBeanDefinition(beanName, serviceBeanDefinition);
            if (logger.isInfoEnabled()) {
                logger.info("The BeanDefinition[" + serviceBeanDefinition + "] of ServiceBean has been registered with name : " + beanName);
            }
        } else {
            if (logger.isWarnEnabled()) {
                logger.warn("The Duplicated BeanDefinition[" + serviceBeanDefinition + "] of ServiceBean[ bean name : " + beanName + "] was be found , Did @DubboComponentScan scan to same package in many times?");
            }
        }
    }

    /**
     * Generates the bean name of {@link ServiceBean}
     *
     * @param service
     * @param interfaceClass           the class of interface annotated {@link Service}
     * @param annotatedServiceBeanName the bean name of annotated {@link Service}
     * @return ServiceBean@interfaceClassName#annotatedServiceBeanName
     * @since 2.5.9
     */
    private String generateServiceBeanName(Service service, Class<?> interfaceClass, String annotatedServiceBeanName) {
        ServiceBeanNameBuilder builder = ServiceBeanNameBuilder.create(service, interfaceClass, environment);
        return builder.build();
    }

    @SuppressWarnings("Duplicates")
    private Class<?> resolveServiceInterfaceClass(Class<?> annotatedServiceBeanClass, Service service) {
        // 首先，从注解本身上获得
        Class<?> interfaceClass = service.interfaceClass();
        if (void.class.equals(interfaceClass)) { // 一般是满足的
            interfaceClass = null;
            // 获得 @Service 注解的 interfaceName 属性。
            String interfaceClassName = service.interfaceName();
            // 如果存在，获得其对应的类
            if (StringUtils.hasText(interfaceClassName)) {
                if (ClassUtils.isPresent(interfaceClassName, classLoader)) {
                    interfaceClass = ClassUtils.resolveClassName(interfaceClassName, classLoader);
                }
            }
        }

        // 【一般情况下，使用这个】获得不到，则从被注解的类上获得其实现的首个接口
        if (interfaceClass == null) {
            Class<?>[] allInterfaces = annotatedServiceBeanClass.getInterfaces();
            if (allInterfaces.length > 0) {
                interfaceClass = allInterfaces[0];
            }
        }

        Assert.notNull(interfaceClass, "@Service interfaceClass() or interfaceName() or interface class must be present!");
        Assert.isTrue(interfaceClass.isInterface(), "The type that was annotated @Service is not an interface!");
        return interfaceClass;
    }

    private Class<?> resolveClass(BeanDefinitionHolder beanDefinitionHolder) {
        BeanDefinition beanDefinition = beanDefinitionHolder.getBeanDefinition();
        return resolveClass(beanDefinition);
    }

    private Class<?> resolveClass(BeanDefinition beanDefinition) {
        String beanClassName = beanDefinition.getBeanClassName();
        return ClassUtils.resolveClassName(beanClassName, classLoader);
    }

    private Set<String> resolvePackagesToScan(Set<String> packagesToScan) {
        Set<String> resolvedPackagesToScan = new LinkedHashSet<String>(packagesToScan.size());
        // 遍历 packagesToScan 数组
        for (String packageToScan : packagesToScan) {
            if (StringUtils.hasText(packageToScan)) {
                // 解析可能存在的占位符
                String resolvedPackageToScan = environment.resolvePlaceholders(packageToScan.trim());
                // 添加到 resolvedPackagesToScan 中
                resolvedPackagesToScan.add(resolvedPackageToScan);
            }
        }
        return resolvedPackagesToScan;
    }

    @SuppressWarnings("Duplicates")
    private AbstractBeanDefinition buildServiceBeanDefinition(Service service, Class<?> interfaceClass, String annotatedServiceBeanName) {
        // 创建 BeanDefinitionBuilder 对象
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(ServiceBean.class);
        // 获得 AbstractBeanDefinition 对象
        AbstractBeanDefinition beanDefinition = builder.getBeanDefinition();

        // 获得 MutablePropertyValues 属性。后续 ，通过向它添加属性，设置到 BeanDefinition 中，即 Service Bean 中。
        MutablePropertyValues propertyValues = beanDefinition.getPropertyValues();

        // 创建 AnnotationPropertyValuesAdapter 对象，添加到 propertyValues 中。
        // 此处，是将注解上的属性，设置到 propertyValues 中
        String[] ignoreAttributeNames = of("provider", "monitor", "application", "module", "registry", "protocol", "interface", "interfaceName"); // 忽略的属性，下面进行单独设置。
        propertyValues.addPropertyValues(new AnnotationPropertyValuesAdapter(service, environment, ignoreAttributeNames));

        // References "ref" property to annotated-@Service Bean
        // 设置 ref 属性指向的 Service Bean 名字
        addPropertyReference(builder, "ref", annotatedServiceBeanName);
        // Set interface 设置 Service 接口类全类名
        builder.addPropertyValue("interface", interfaceClass.getName());

        /**
         * Add {@link org.apache.dubbo.config.ProviderConfig} Bean reference
         *
         * 添加 provider 属性对应的 ProviderConfig Bean 对象
         */
        String providerConfigBeanName = service.provider();
        if (StringUtils.hasText(providerConfigBeanName)) {
            addPropertyReference(builder, "provider", providerConfigBeanName);
        }

        /**
         * Add {@link org.apache.dubbo.config.MonitorConfig} Bean reference
         *
         * 添加 monitor 属性对应的 MonitorConfig Bean 对象
         */
        String monitorConfigBeanName = service.monitor();
        if (StringUtils.hasText(monitorConfigBeanName)) {
            addPropertyReference(builder, "monitor", monitorConfigBeanName);
        }

        /**
         * Add {@link org.apache.dubbo.config.ApplicationConfig} Bean reference
         *
         * 添加 application 属性对应的 ApplicationConfig Bean 对象
         */
        String applicationConfigBeanName = service.application();
        if (StringUtils.hasText(applicationConfigBeanName)) {
            addPropertyReference(builder, "application", applicationConfigBeanName);
        }

        /**
         * Add {@link org.apache.dubbo.config.ModuleConfig} Bean reference
         *
         * 添加 module 属性对应的 ModuleConfig Bean 对象
         */
        String moduleConfigBeanName = service.module();
        if (StringUtils.hasText(moduleConfigBeanName)) {
            addPropertyReference(builder, "module", moduleConfigBeanName);
        }


        /**
         * Add {@link org.apache.dubbo.config.RegistryConfig} Bean reference
         *
         * 添加 registries 属性对应的 RegistryConfig Bean 数组（一个或多个）
         */
        String[] registryConfigBeanNames = service.registry();
        List<RuntimeBeanReference> registryRuntimeBeanReferences = toRuntimeBeanReferences(registryConfigBeanNames);
        if (!registryRuntimeBeanReferences.isEmpty()) {
            builder.addPropertyValue("registries", registryRuntimeBeanReferences);
        }

        /**
         * Add {@link org.apache.dubbo.config.ProtocolConfig} Bean reference
         *
         * 添加 protocols 属性对应的 ProtocolConfig Bean 数组（一个或多个）
         */
        String[] protocolConfigBeanNames = service.protocol();
        List<RuntimeBeanReference> protocolRuntimeBeanReferences = toRuntimeBeanReferences(protocolConfigBeanNames);
        if (!protocolRuntimeBeanReferences.isEmpty()) {
            builder.addPropertyValue("protocols", protocolRuntimeBeanReferences);
        }

        return builder.getBeanDefinition();
    }

    // RuntimeBeanReference ，在解析到依赖的Bean的时侯，解析器会依据依赖bean的name创建一个RuntimeBeanReference对像，将这个对像放入BeanDefinition的MutablePropertyValues中。
    // 此处，和上面不太一样的原因，因为是多个
    @SuppressWarnings("Duplicates")
    private ManagedList<RuntimeBeanReference> toRuntimeBeanReferences(String... beanNames) {
        ManagedList<RuntimeBeanReference> runtimeBeanReferences = new ManagedList<RuntimeBeanReference>();
        if (!ObjectUtils.isEmpty(beanNames)) {
            for (String beanName : beanNames) {
                // 解析真正的 Bean 名字，如果有占位符的话
                String resolvedBeanName = environment.resolvePlaceholders(beanName);
                runtimeBeanReferences.add(new RuntimeBeanReference(resolvedBeanName));
            }
        }
        return runtimeBeanReferences;
    }

    // 添加属性值是引用类型
    private void addPropertyReference(BeanDefinitionBuilder builder, String propertyName, String beanName) {
        String resolvedBeanName = environment.resolvePlaceholders(beanName);
        builder.addPropertyReference(propertyName, resolvedBeanName);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

}